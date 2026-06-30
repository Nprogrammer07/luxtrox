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
 * Comisiones de referido -- VERSION SIMPLIFICADA (adenda posterior a
 * las Fases 6, 8 y 17 del domain-model.md).
 *
 * Reglas actuales:
 *   - El referente NO necesita haber comprado ningun plan. Cualquier
 *     persona que tenga un codigo de referido activo recibe comision
 *     automaticamente cuando alguien se registra con ese codigo y hace
 *     una compra.
 *   - La comision se calcula por CADA compra confirmada del referido,
 *     sin limite: Driver = 9% del precio, Zenith = 22% del precio.
 *   - Se acredita SIEMPRE de forma directa a available_balance del
 *     referente (REFERRAL_BONUS_DIRECT), sin verificar si tiene
 *     posicion activa ni licencia Zenith.
 *   - El Referral se marca RESOLVED la primera vez que el referido
 *     compra algo (para que el admin distinga "todavia no compro" de
 *     "ya hay al menos una comision pagada"), pero RESOLVED ya NO
 *     bloquea evaluaciones futuras -- cada compra sigue generando
 *     su propia comision.
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

    /** Driver = 9% del precio, Zenith = 22% del precio. */
    public BigDecimal calculateCommission(Purchase referredPurchase) {
        BigDecimal rate = referredPurchase.getPlanType() == PlanType.DRIVER
                ? PlanPricing.DRIVER_REFERRAL_RATE
                : PlanPricing.ZENITH_REFERRAL_RATE;
        return referredPurchase.getTotalAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Disparador: una compra del referido se confirma. Paga comision
     * directa al balance del referente, sin restricciones de plan.
     *
     * USA referralRepository.findByReferred() como primer paso (no
     * referredUser.getReferredBy()) para evitar problemas de proxy
     * LAZY en el contexto HTTP: cuando Purchase.user es un proxy de
     * Hibernate sin inicializar completamente, getReferredBy() puede
     * devolver null aunque el campo este correctamente guardado en BD.
     * La busqueda en repositorio evita esa ambigüedad -- si no hay
     * fila de Referral, el usuario no fue referido por nadie.
     */
    @Transactional
    public void onReferredPurchaseConfirmed(Purchase confirmedPurchase) {
        User referredUser = confirmedPurchase.getUser();

        Optional<Referral> referralOpt = referralRepository.findByReferred(referredUser);
        if (referralOpt.isEmpty()) {
            return; // no fue referido por nadie
        }

        Referral referral = referralOpt.get();
        User referrer = referral.getReferrer();
        BigDecimal commission = calculateCommission(confirmedPurchase);

        // Transaccion: REFERRAL_BONUS_DIRECT siempre (sin posicion asociada)
        CashbackTransaction tx = new CashbackTransaction(
                referrer, CashbackTransactionType.REFERRAL_BONUS_DIRECT, commission);
        tx.setSourceReferral(referral);
        cashbackTransactionRepository.save(tx);

        // Credito directo al balance
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

        // Primera compra: registrar fecha y marcar RESOLVED (solo informativo)
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
