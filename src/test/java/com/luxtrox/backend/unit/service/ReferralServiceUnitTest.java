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
 * Comisiones de referido -- planes Zenith (22%) y Plus (25% = $50 flat).
 * Sin restricciones de plan: cualquier persona con codigo de referido
 * recibe comision por CADA compra confirmada del referido.
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
    void calculateCommission_zenith_isTwentyTwoPercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("505.78");
    }

    @Test
    void calculateCommission_plus_isTwentyFivePercent() {
        Purchase purchase = confirmedPurchase(referred, PlanType.PLUS, new BigDecimal("200.00"));
        assertThat(service.calculateCommission(purchase)).isEqualByComparingTo("50.00");
    }

    // ---------- sin referente ----------

    @Test
    void buyerHasNoReferrer_doesNothingAtAll() {
        referred.setReferredBy(null);
        Purchase purchase = confirmedPurchase(referred, PlanType.ZENITH, new BigDecimal("2299.00"));
        when(referralRepository.findByReferred(referred)).thenReturn(Optional.empty());

        service.onReferredPurchaseConfirmed(purchase);

        verify(referralRepository).findByReferred(referred);
        verifyNoInteractions(cashbackTransactionRepository, userRepository,
                notificationEmailService, auditService);
        verify(referralRepository, never()).save(any());
    }

    // ---------- Commission directa ----------

    @Test
    void plusPurchase_referrerWithNoPlan_stillReceivesFiftyUsd() {
        Purchase purchase = confirmedPurchase(referred, PlanType.PLUS, new BigDecimal("200.00")); // 50.00
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));

        service.onReferredPurchaseConfirmed(purchase);

        ArgumentCaptor<CashbackTransaction> txCaptor = ArgumentCaptor.forClass(CashbackTransaction.class);
        verify(cashbackTransactionRepository).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("50.00");
        assertThat(txCaptor.getValue().getType()).isEqualTo(CashbackTransactionType.REFERRAL_BONUS_DIRECT);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("50.00");

        verify(notificationEmailService).sendReferralBonusReceivedEmail(referrer, new BigDecimal("50.00"));

        assertThat(meterRegistry.get("luxtrox.referral.commission.paid")
                .tag("planType", "PLUS").counter().count()).isEqualTo(50.00);
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

    // ---------- Por compra (repetible) ----------

    @Test
    void firstPurchase_referralMovesToResolved() {
        Purchase purchase = confirmedPurchase(referred, PlanType.PLUS, new BigDecimal("200.00"));
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
        Purchase secondPurchase = confirmedPurchase(referred, PlanType.PLUS, new BigDecimal("200.00"));
        Referral alreadyResolved = newReferral();
        alreadyResolved.setStatus(ReferralStatus.RESOLVED);

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(alreadyResolved));

        service.onReferredPurchaseConfirmed(secondPurchase);

        verify(cashbackTransactionRepository).save(any(CashbackTransaction.class));
        verify(userRepository).save(any(User.class));
        ArgumentCaptor<Referral> referralCaptor = ArgumentCaptor.forClass(Referral.class);
        verify(referralRepository).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo(ReferralStatus.RESOLVED);
    }

    // ---------- Fallo de email ----------

    @Test
    void emailFailure_stillCompletesThePaymentAndAudits() {
        Purchase purchase = confirmedPurchase(referred, PlanType.PLUS, new BigDecimal("200.00"));
        Referral referral = newReferral();

        when(referralRepository.findByReferred(referred)).thenReturn(Optional.of(referral));
        doThrow(new RuntimeException("Resend caido")).when(notificationEmailService)
                .sendReferralBonusReceivedEmail(any(), any());

        service.onReferredPurchaseConfirmed(purchase);

        verify(userRepository).save(any(User.class));
        verify(auditService).recordSystemAction(eq("User"), any(),
                eq("REFERRAL_EMAIL_NOTIFICATION_FAILED"), any(), any());
    }
}