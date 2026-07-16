package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Comisiones de referido. Sin requisito de plan propio: cualquier
 * usuario con código de referido cobra comisión por cada compra
 * confirmada de su referido.
 *
 * Zenith  = 22% del precio de la compra.
 * Genius (PLUS) = $19 FIJOS (monto fijo, no un porcentaje).
 *
 * Genius es pago único, así que no genera comisiones por renovación.
 * El método onRenewalConfirmed() queda latente por si en el futuro se
 * reactiva algún esquema de renovación con comisión.
 */
@Service
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final AuditService auditService;
    private final NotificationEmailService notificationEmailService;
    private final MeterRegistry meterRegistry;

    public ReferralService(ReferralRepository referralRepository,
                            UserRepository userRepository,
                            CashbackTransactionRepository cashbackTransactionRepository,
                            AuditService auditService,
                            NotificationEmailService notificationEmailService,
                            MeterRegistry meterRegistry) {
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.auditService = auditService;
        this.notificationEmailService = notificationEmailService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Comisión de una compra según el plan:
     *  - Zenith: 22% del monto de la compra.
     *  - Genius (PLUS): $19 fijos, sin importar el monto.
     */
    public BigDecimal calculateCommission(Purchase referredPurchase) {
        return switch (referredPurchase.getPlanType()) {
            case ZENITH -> referredPurchase.getTotalAmount()
                    .multiply(PlanPricing.ZENITH_REFERRAL_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            case PLUS -> PlanPricing.PLUS_REFERRAL_FLAT;
        };
    }

    /** Comisión por una COMPRA confirmada del referido. */
    @Transactional
    public void onReferredPurchaseConfirmed(Purchase confirmedPurchase) {
        BigDecimal commission = calculateCommission(confirmedPurchase);
        payCommission(confirmedPurchase.getUser(), confirmedPurchase.getPlanType(),
                commission, confirmedPurchase);
    }

    /**
     * LATENTE -- hoy sin uso. Genius es pago único y no genera comisión
     * por renovación. Se conserva por si se reactiva un esquema de
     * renovación con comisión en el futuro. Para Genius usaría el monto
     * fijo; para cualquier otro plan, la tasa correspondiente.
     */
    @Transactional
    public void onRenewalConfirmed(User renewingUser, PlanType plan, BigDecimal renewalAmount) {
        BigDecimal commission = switch (plan) {
            case ZENITH -> renewalAmount.multiply(PlanPricing.ZENITH_REFERRAL_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            case PLUS -> PlanPricing.PLUS_REFERRAL_FLAT;
        };
        payCommission(renewingUser, plan, commission, null);
    }

    /**
     * Núcleo del pago: acredita la comisión al referente del usuario que
     * compró. Si el usuario no tiene referente, no hace nada.
     * triggeringPurchase puede ser null.
     */
    private void payCommission(User buyer, PlanType plan, BigDecimal commission,
                                Purchase triggeringPurchase) {
        Optional<Referral> referralOpt = referralRepository.findByReferred(buyer);
        if (referralOpt.isEmpty()) return;

        Referral referral = referralOpt.get();
        User referrer = referral.getReferrer();

        CashbackTransaction tx = new CashbackTransaction(
                referrer, CashbackTransactionType.REFERRAL_BONUS_DIRECT, commission);
        tx.setSourceReferral(referral);
        cashbackTransactionRepository.save(tx);

        BigDecimal oldBalance = referrer.getAvailableBalance();
        referrer.setAvailableBalance(oldBalance.add(commission));
        userRepository.save(referrer);

        auditService.record(referrer, "User", referrer.getId(), "REFERRAL_COMMISSION_PAID",
                oldBalance, referrer.getAvailableBalance());

        Counter.builder("luxtrox.referral.commission.paid")
                .tag("planType", plan.name())
                .register(meterRegistry)
                .increment(commission.doubleValue());

        referral.setBonusPaidAt(OffsetDateTime.now());
        if (triggeringPurchase != null) {
            referral.setTriggeringPurchase(triggeringPurchase);
        }
        if (referral.getStatus() == ReferralStatus.PENDING_PURCHASE) {
            referral.setQualifiedAt(OffsetDateTime.now());
            referral.setStatus(ReferralStatus.RESOLVED);
        }
        referralRepository.save(referral);

        try {
            notificationEmailService.sendReferralBonusReceivedEmail(referrer, commission);
        } catch (Exception e) {
            auditService.recordSystemAction("User", referrer.getId(),
                    "REFERRAL_EMAIL_NOTIFICATION_FAILED", null, e.getMessage());
        }
    }
}
