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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unitario puro con Mockito -- version corregida (ver
 * docs/domain-model.md adenda de Fase 8). El diseno anterior (que
 * pagaba a cualquier referente con una compra confirmada de cualquier
 * tipo, con reintento) quedo completamente reemplazado: ahora la
 * comision de Driver requiere posicion Driver propia activa, la de
 * Zenith requiere licencia Zenith propia ACTIVE, y la evaluacion es
 * unica -- sin reintentos.
 */
@ExtendWith(MockitoExtension.class)
class ReferralServiceUnitTest {

    @Mock private ReferralRepository referralRepository;
    @Mock private UserRepository userRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private ZenithLicenseRepository zenithLicenseRepository;
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
        service = new ReferralService(referralRepository, userRepository, positionRepository,
                zenithLicenseRepository, cashbackTransactionRepository, auditService, notificationEmailService,
                meterRegistry);

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

    private InvestmentPosition activePosition(User owner, BigDecimal remaining) {
        Purchase purchase = confirmedPurchase(owner, PlanType.DRIVER, new BigDecimal("1099.00"));
        InvestmentPosition position = new InvestmentPosition(owner, purchase, new BigDecimal("1099.00"),
                new BigDecimal("3297.00"));
        position.setCashbackRemaining(remaining);
        position.setCashbackPaid(position.getTargetCashback().subtract(remaining));
        setId(position, UUID.randomUUID());
        return position;
    }

    private Referral newReferral() {
        return new Referral(referrer, referred, "REFA0001");
    }

    // ---------- calculateCommission() (sin cambios respecto al diseno original) ----------

    @Test
    void calculateCommission_driver_isNinePercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("98.91");
    }

    @Test
    void calculateCommission_zenith_isFortyPercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("919.60");
    }

    // ---------- onReferredPurchaseConfirmed() -- sin referente ----------

    @Test
    void buyerHasNoReferrer_doesNothingAtAll() {
        referred.setReferredBy(null); // sobreescribe el setUp -- este caso especifico no tiene referente
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));

        service.onReferredPurchaseConfirmed(purchase);

        verifyNoInteractions(referralRepository, positionRepository, zenithLicenseRepository,
                cashbackTransactionRepository, userRepository, notificationEmailService, auditService);
    }

    // ---------- DRIVER: con posicion activa que cubre todo ----------

    @Test
    void driverSale_referrerHasActivePositionThatFullyCovers_paysAsAdvanceOnPosition() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00")); // comision = 98.91
        Referral referral = newReferral();
        InvestmentPosition position = activePosition(referrer, new BigDecimal("200.00"));

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(positionRepository.findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                referrer, PositionStatus.ACTIVE, BigDecimal.ZERO)).thenReturn(List.of(position));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<InvestmentPosition> positionCaptor = ArgumentCaptor.forClass(InvestmentPosition.class);
        verify(positionRepository).save(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getCashbackRemaining()).isEqualByComparingTo("101.09");
        assertThat(positionCaptor.getValue().getStatus()).isEqualTo(PositionStatus.ACTIVE);

        verify(cashbackTransactionRepository, times(1)).save(any(CashbackTransaction.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("98.91");

        verify(notificationEmailService).sendReferralBonusReceivedEmail(referrer, new BigDecimal("98.91"));

        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo(ReferralStatus.RESOLVED);

        assertThat(meterRegistry.get("luxtrox.referral.commission.paid").tag("planType", "DRIVER")
                .counter().count()).isEqualTo(98.91);
    }

    @Test
    void driverSale_commissionExceedsRemainingCashback_excessIsForfeitedNotPaidElsewhere() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00")); // comision = 98.91
        Referral referral = newReferral();
        InvestmentPosition position = activePosition(referrer, new BigDecimal("50.00")); // solo le quedan 50

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(positionRepository.findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                referrer, PositionStatus.ACTIVE, BigDecimal.ZERO)).thenReturn(List.of(position));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<InvestmentPosition> positionCaptor = ArgumentCaptor.forClass(InvestmentPosition.class);
        verify(positionRepository).save(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getCashbackRemaining()).isEqualByComparingTo("0.00");
        assertThat(positionCaptor.getValue().getStatus()).isEqualTo(PositionStatus.COMPLETED);

        // Solo se paga lo que cupo (50.00) -- UNA transaccion, no dos.
        ArgumentCaptor<CashbackTransaction> txCaptor = ArgumentCaptor.forClass(CashbackTransaction.class);
        verify(cashbackTransactionRepository, times(1)).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("50.00");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("50.00"); // NO 98.91

        // El excedente (48.91) debe quedar registrado como perdido, nunca pagado.
        verify(auditService).recordSystemAction(eq("User"), any(),
                eq("REFERRAL_COMMISSION_FORFEITED_DRIVER_EXCEEDS_REMAINING"), any(), eq(new BigDecimal("48.91")));
    }

    // ---------- DRIVER: sin ninguna posicion activa -- se pierde TODO ----------

    @Test
    void driverSale_referrerHasNoActivePosition_entireCommissionIsForfeited() {
        Purchase purchase = confirmedPurchase(referred, PlanType.DRIVER, new BigDecimal("1099.00"));
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(positionRepository.findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                referrer, PositionStatus.ACTIVE, BigDecimal.ZERO)).thenReturn(List.of()); // ninguna activa

        service.onReferredPurchaseConfirmed(purchase);

        verifyNoInteractions(cashbackTransactionRepository, userRepository, notificationEmailService);
        verify(positionRepository, never()).save(any());

        verify(auditService).recordSystemAction(eq("User"), any(),
                eq("REFERRAL_COMMISSION_FORFEITED_DRIVER_SIN_POSICION_ACTIVA"), any(), eq(new BigDecimal("98.91")));

        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo(ReferralStatus.RESOLVED); // se evaluo, aunque no se pago

        assertThat(meterRegistry.get("luxtrox.referral.commission.forfeited")
                .tag("reason", "DRIVER_SIN_POSICION_ACTIVA").counter().count()).isEqualTo(98.91);
    }

    // ---------- ZENITH: con licencia ACTIVE ----------

    @Test
    void zenithSale_referrerHasActiveLicense_paysFullyAndDirectly() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00")); // comision = 919.60
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(zenithLicenseRepository.existsByUserAndStatus(referrer, ZenithLicenseStatus.ACTIVE)).thenReturn(true);

        service.onReferredPurchaseConfirmed(purchase);

        verify(positionRepository, never()).save(any()); // Zenith nunca toca una posicion Driver

        ArgumentCaptor<CashbackTransaction> txCaptor = ArgumentCaptor.forClass(CashbackTransaction.class);
        verify(cashbackTransactionRepository, times(1)).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("919.60");
        assertThat(txCaptor.getValue().getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS_DIRECT);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("919.60");
    }

    // ---------- ZENITH: SIN licencia activa (nunca tuvo, o la tiene EXPIRED) -- se pierde TODO ----------

    @Test
    void zenithSale_referrerHasNoActiveLicense_entireCommissionIsForfeited() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(zenithLicenseRepository.existsByUserAndStatus(referrer, ZenithLicenseStatus.ACTIVE)).thenReturn(false);

        service.onReferredPurchaseConfirmed(purchase);

        verifyNoInteractions(cashbackTransactionRepository, userRepository, notificationEmailService);
        verify(auditService).recordSystemAction(eq("User"), any(),
                eq("REFERRAL_COMMISSION_FORFEITED_ZENITH_SIN_LICENCIA_ACTIVA"), any(), eq(new BigDecimal("919.60")));
    }

    // ---------- Fallo de email no tumba un pago ya resuelto ----------

    @Test
    void emailFailure_stillCompletesThePaymentAndAudits() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        when(zenithLicenseRepository.existsByUserAndStatus(referrer, ZenithLicenseStatus.ACTIVE)).thenReturn(true);
        doThrow(new RuntimeException("Resend caido")).when(notificationEmailService)
                .sendReferralBonusReceivedEmail(any(), any());

        service.onReferredPurchaseConfirmed(purchase); // no debe propagar la excepcion

        verify(userRepository).save(any(User.class)); // el pago SI se completo
        verify(auditService).recordSystemAction(eq("User"), any(), eq("REFERRAL_EMAIL_NOTIFICATION_FAILED"), any(), any());
    }
}