package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.*;
import com.luxtrox.backend.repository.*;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.NotificationEmailService;
import com.luxtrox.backend.service.ReferralService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comisiones de referido -- VERSION SIMPLIFICADA.
 * Sin restricciones de plan: cualquier persona con codigo de referido
 * recibe comision por CADA compra confirmada del referido,
 * independientemente del plan y sin haber comprado ningun plan propio.
 */
@ExtendWith(MockitoExtension.class)
class ReferralServiceUnitTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private UserRepository userRepository;
    @Mock private CashbackTransactionRepository cashbackTransactionRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationEmailService notificationEmailService;

    private ReferralService service;
    private MeterRegistry meterRegistry;
    private Role role;
    private User referrer;
    private User referred;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ReferralService(referralRepository, userRepository,
                cashbackTransactionRepository, auditService, notificationEmailService, meterRegistry);

        role = new Role("USER", "Usuario estandar");
        referrer = new User("Referente", "referente@example.com", "+1", "hash", role, "REFA0001");
        setId(referrer, UUID.randomUUID());
        referred = new User("Referido", "referido@example.com", "+1", "hash", role, "REFB0001");
        setId(referred, UUID.randomUUID());
        referred.setReferredBy(referrer);
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Purchase confirmedPurchase(User user, PlanType plan, BigDecimal amount) {
        Purchase purchase = new Purchase(user, plan, 1, amount, PaymentMethod.CRYPTO);
        purchase.setStatus(PurchaseStatus.CONFIRMED);
        setId(purchase, UUID.randomUUID());
        return purchase;
    }

    private Referral newReferral() {
        return new Referral(referrer, referred, "REFA0001");
    }

    // ---------- calculateCommission() ----------

    @Test
    void calculateCommission_driver_isNinePercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("98.91");
    }

    @Test
    void calculateCommission_zenith_isTwentyTwoPercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("505.78");
    }

    // ---------- onReferredPurchaseConfirmed() -- sin referente ----------

    @Test
    void buyerHasNoReferrer_doesNothingAtAll() {
        referred.setReferredBy(null);
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        // El repositorio es ahora la fuente de verdad -- se consulta siempre.
        // Si no hay fila de Referral, devolvemos empty y no se hace nada mas.
        when(referralRepository.findByReferred(referred)).thenReturn(java.util.Optional.empty());

        service.onReferredPurchaseConfirmed(purchase);

        verify(referralRepository).findByReferred(referred); // SI se consulto
        verifyNoInteractions(cashbackTransactionRepository, userRepository,
                notificationEmailService, auditService);
        verify(referralRepository, never()).save(any()); // pero NO se guardo nada
    }

    // ---------- Commission directa -- sin restricciones de plan ----------

    @Test
    void driverPurchase_referrerWithNoPlan_stillReceivesNinePercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00")); // 98.91
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<CashbackTransaction> txCaptor = ArgumentCaptor.forClass(CashbackTransaction.class);
        verify(cashbackTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("98.91");
        assertThat(txCaptor.getValue().getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS_DIRECT);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("98.91");

        verify(notificationEmailService).sendReferralBonusReceivedEmail(referrer, new BigDecimal("98.91"));

        assertThat(meterRegistry.get("luxtrox.referral.commission.paid")
                .tag("planType", "DRIVER").counter().count()).isEqualTo(98.91);
    }

    @Test
    void zenithPurchase_referrerWithNoPlan_stillReceivesTwentyTwoPercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00")); // 505.78
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<CashbackTransaction> txCaptor = ArgumentCaptor.forClass(CashbackTransaction.class);
        verify(cashbackTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("505.78");
        assertThat(txCaptor.getValue().getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS_DIRECT);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("505.78");

        assertThat(meterRegistry.get("luxtrox.referral.commission.paid")
                .tag("planType", "ZENITH").counter().count()).isEqualTo(505.78);
    }

    // ---------- Por compra (repetible) -- RESOLVED ya no bloquea ----------

    @Test
    void firstPurchase_referralMovesToResolved() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        Referral referral = newReferral();
        assertThat(referral.getStatus()).isEqualTo(ReferralStatus.PENDING_PURCHASE);

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo(ReferralStatus.RESOLVED);
    }

    @Test
    void secondPurchaseFromSameReferredPerson_referralAlreadyResolved_stillPaysCommission() {
        // ANTES: RESOLVED bloqueaba nuevas evaluaciones (una por persona).
        // AHORA: cada compra genera su propia comision, sin limite.
        Purchase secondPurchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        Referral alreadyResolved = newReferral();
        alreadyResolved.setStatus(ReferralStatus.RESOLVED);

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(alreadyResolved));

        service.onReferredPurchaseConfirmed(secondPurchase);

        // La comision SI se paga aunque el Referral ya estuviera RESOLVED.
        verify(cashbackTransactionRepository).save(any(CashbackTransaction.class));
        verify(userRepository).save(any(User.class));
        // El Referral YA estaba RESOLVED, no cambia de nuevo.
        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo(ReferralStatus.RESOLVED);
    }

    // ---------- Fallo de email no tumba el pago ----------

    @Test
    void emailFailure_stillCompletesThePaymentAndAudits() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        doThrow(new RuntimeException("Resend caido")).when(notificationEmailService)
                .sendReferralBonusReceivedEmail(any(), any());

        service.onReferredPurchaseConfirmed(purchase); // no debe propagar la excepcion

        verify(userRepository).save(any(User.class)); // el pago SI se completo
        verify(auditService).recordSystemAction(eq("User"), any(),
                eq("REFERRAL_EMAIL_NOTIFICATION_FAILED"), any(), any());
    }
}