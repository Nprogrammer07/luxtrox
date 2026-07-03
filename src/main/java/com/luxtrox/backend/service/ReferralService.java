package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Comisiones de referido — sin restricciones de plan, por compra.
 * Driver = 9%, Zenith = 22%, Plus = 25% ($50 flat).
 * Todas van directo al available_balance (REFERRAL_BONUS_DIRECT).
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

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

    /** Driver=9%, Zenith=22%, Plus=25% de la compra. */
    public BigDecimal calculateCommission(Purchase referredPurchase) {
        BigDecimal rate = switch (referredPurchase.getPlanType()) {
            case DRIVER -> PlanPricing.DRIVER_REFERRAL_RATE;
            case ZENITH -> PlanPricing.ZENITH_REFERRAL_RATE;
            case PLUS   -> PlanPricing.PLUS_REFERRAL_RATE;
        };
        return referredPurchase.getTotalAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void onReferredPurchaseConfirmed(Purchase confirmedPurchase) {
        User referredUser = confirmedPurchase.getUser();

        Optional<Referral> referralOpt = referralRepository.findByReferred(referredUser);
        if (referralOpt.isEmpty()) {
            return;
        }

        Referral referral = referralOpt.get();
        User referrer = referral.getReferrer();
        BigDecimal commission = calculateCommission(confirmedPurchase);

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
                .description("Total en dolares pagado por comisiones de referido")
                .tag("planType", confirmedPurchase.getPlanType().name())
                .register(meterRegistry)
                .increment(commission.doubleValue());

        referral.setBonusPaidAt(OffsetDateTime.now());
        referral.setTriggeringPurchase(confirmedPurchase);

        if (referral.getStatus() == ReferralStatus.PENDING_PURCHASE) {
            referral.setQualifiedAt(OffsetDateTime.now());
            referral.setStatus(ReferralStatus.RESOLVED);
        }
        referralRepository.save(referral);

        notifyReferrerQuietly(referrer, commission);
    }

    private void notifyReferrerQuietly(User referrer, BigDecimal commission) {
        try {
            notificationEmailService.sendReferralBonusReceivedEmail(referrer, commission);
        } catch (Exception e) {
            auditService.recordSystemAction("User", referrer.getId(),
                    "REFERRAL_EMAIL_NOTIFICATION_FAILED", null, e.getMessage());
        }
    }
}
