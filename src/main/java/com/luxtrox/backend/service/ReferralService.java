package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PositionStatus;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
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
 * Implementa el algoritmo de comisiones de referido -- VERSION
 * CORREGIDA (ver docs/domain-model.md adenda de Fase 8). Reemplaza
 * por completo el diseno anterior (Fase 6), que pagaba la comision
 * completa a cualquier referente con AL MENOS UNA compra confirmada
 * de cualquier tipo, con reintento si todavia no calificaba.
 *
 * Regla real del negocio (aclarada por el cliente):
 *   - Comision por venta de DRIVER: solo se paga si el referente tiene
 *     una posicion Driver propia ACTIVA (cashback pendiente). Se
 *     aplica como avance a esa posicion, tope = lo que le quede
 *     pendiente -- lo que exceda esa capacidad SE PIERDE (no hay
 *     pago directo a balance para Driver). Si no tiene ninguna
 *     posicion activa, TODA la comision se pierde.
 *   - Comision por venta de ZENITH: se paga completa y directa a
 *     available_balance, solo si el referente tiene una licencia
 *     Zenith ACTIVE en este momento. Si no, se pierde completa.
 *   - La evaluacion es UNICA: en el momento exacto en que se confirma
 *     la compra del referido. Sin reintentos, sin espera -- si el
 *     referente no califica en ese instante, la comision se pierde
 *     para siempre. Por eso ya NO existe un disparador equivalente a
 *     "onReferrerPurchaseConfirmed".
 *   - Cada compra del referido se evalua de forma INDEPENDIENTE: si
 *     refiere tanto un Driver como un Zenith, cada venta genera su
 *     propia evaluacion contra el plan correspondiente del referente.
 */
@Service
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final InvestmentPositionRepository positionRepository;
    private final ZenithLicenseRepository zenithLicenseRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final AuditService auditService;
    private final NotificationEmailService notificationEmailService;
    private final MeterRegistry meterRegistry;

    public ReferralService(ReferralRepository referralRepository,
                            UserRepository userRepository,
                            InvestmentPositionRepository positionRepository,
                            ZenithLicenseRepository zenithLicenseRepository,
                            CashbackTransactionRepository cashbackTransactionRepository,
                            AuditService auditService,
                            NotificationEmailService notificationEmailService,
                            MeterRegistry meterRegistry) {
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.zenithLicenseRepository = zenithLicenseRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.auditService = auditService;
        this.notificationEmailService = notificationEmailService;
        this.meterRegistry = meterRegistry;
    }

    /** Driver = 9% del precio, Zenith = 40% del precio -- sin cambios respecto al diseno original. */
    public BigDecimal calculateCommission(Purchase referredPurchase) {
        BigDecimal rate = referredPurchase.getPlanType() == PlanType.DRIVER
                ? PlanPricing.DRIVER_REFERRAL_RATE
                : PlanPricing.ZENITH_REFERRAL_RATE;
        return referredPurchase.getTotalAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Unico disparador que queda: la compra del REFERIDO se confirma.
     * Evalua y resuelve la comision DE INMEDIATO, sin reintentos.
     */
    @Transactional
    public void onReferredPurchaseConfirmed(Purchase confirmedPurchase) {
        User referredUser = confirmedPurchase.getUser();
        if (referredUser.getReferredBy() == null) {
            return; // no fue referido por nadie
        }

        Referral referral = referralRepository.findByReferred(referredUser)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario " + referredUser.getId() + " tiene referredBy pero no existe su Referral"));

        if (referral.getQualifiedAt() == null) {
            referral.setQualifiedAt(OffsetDateTime.now());
        }
        // Nota: si el mismo referido compra mas de un plan, este campo
        // (y targetPosition mas abajo) solo conserva el snapshot de la
        // evaluacion MAS RECIENTE -- el historial real, completo, vive
        // en cashback_transactions y audit_logs, que son append-only.
        referral.setTriggeringPurchase(confirmedPurchase);

        if (confirmedPurchase.getPlanType() == PlanType.DRIVER) {
            resolveDriverCommission(referral, confirmedPurchase);
        } else {
            resolveZenithCommission(referral, confirmedPurchase);
        }

        referral.setStatus(ReferralStatus.RESOLVED);
        referralRepository.save(referral);
    }

    /**
     * Tope = lo que le quede pendiente al referente en su posicion
     * Driver activa mas reciente. El excedente sobre ese tope SE
     * PIERDE -- a proposito, no se reasigna a otra posicion ni se
     * paga directo a balance (a diferencia del rendimiento mensual,
     * que si cascada entre posiciones).
     */
    private void resolveDriverCommission(Referral referral, Purchase referredPurchase) {
        User referrer = referral.getReferrer();
        BigDecimal commission = calculateCommission(referredPurchase);

        Optional<InvestmentPosition> destino = positionRepository
                .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                        referrer, PositionStatus.ACTIVE, BigDecimal.ZERO)
                .stream()
                .findFirst();

        if (destino.isEmpty()) {
            forfeitCommission(referrer, commission, "DRIVER_SIN_POSICION_ACTIVA");
            return;
        }

        InvestmentPosition position = destino.get();
        BigDecimal applied = commission.min(position.getCashbackRemaining());
        BigDecimal lost = commission.subtract(applied);

        BigDecimal oldPaid = position.getCashbackPaid();
        BigDecimal oldRemaining = position.getCashbackRemaining();
        position.setCashbackPaid(position.getCashbackPaid().add(applied));
        position.setCashbackRemaining(position.getCashbackRemaining().subtract(applied));
        if (position.getCashbackRemaining().compareTo(BigDecimal.ZERO) == 0) {
            position.setStatus(PositionStatus.COMPLETED);
            position.setCompletedAt(OffsetDateTime.now());
        }
        positionRepository.save(position);

        CashbackTransaction tx = new CashbackTransaction(position, CashbackTransactionType.REFERRAL_BONUS, applied);
        tx.setSourceReferral(referral);
        cashbackTransactionRepository.save(tx);

        auditService.record(referrer, "InvestmentPosition", position.getId(), "REFERRAL_BONUS_APPLIED",
                new Object[]{oldPaid, oldRemaining},
                new Object[]{position.getCashbackPaid(), position.getCashbackRemaining()});

        referral.setTargetPosition(position);
        creditBalanceAndNotify(referrer, applied, referral, "DRIVER");

        if (lost.compareTo(BigDecimal.ZERO) > 0) {
            forfeitCommission(referrer, lost, "DRIVER_EXCEEDS_REMAINING");
        }
    }

    /**
     * Zenith no tiene cashback -- por eso aqui no hay tope ni
     * reparto, solo una verificacion binaria (licencia ACTIVE o no) y
     * el pago directo y completo si corresponde.
     */
    private void resolveZenithCommission(Referral referral, Purchase referredPurchase) {
        User referrer = referral.getReferrer();
        BigDecimal commission = calculateCommission(referredPurchase);

        boolean hasActiveZenith = zenithLicenseRepository.existsByUserAndStatus(referrer, ZenithLicenseStatus.ACTIVE);
        if (!hasActiveZenith) {
            forfeitCommission(referrer, commission, "ZENITH_SIN_LICENCIA_ACTIVA");
            return;
        }

        CashbackTransaction tx = new CashbackTransaction(referrer, commission);
        tx.setSourceReferral(referral);
        cashbackTransactionRepository.save(tx);

        creditBalanceAndNotify(referrer, commission, referral, "ZENITH");
    }

    private void creditBalanceAndNotify(User referrer, BigDecimal amount, Referral referral, String planTypeTag) {
        BigDecimal oldBalance = referrer.getAvailableBalance();
        referrer.setAvailableBalance(referrer.getAvailableBalance().add(amount));
        userRepository.save(referrer);

        auditService.record(referrer, "User", referrer.getId(), "REFERRAL_COMMISSION_PAID",
                oldBalance, referrer.getAvailableBalance());

        Counter.builder("luxtrox.referral.commission.paid")
                .description("Total en dolares pagado por comisiones de referido")
                .tag("planType", planTypeTag)
                .register(meterRegistry)
                .increment(amount.doubleValue());

        referral.setBonusPaidAt(OffsetDateTime.now());
        notifyReferrerQuietly(referrer, amount);
    }

    /**
     * Ninguna transaccion de dinero -- solo queda registro en
     * audit_logs para soporte/trazabilidad. La metrica permite ver,
     * sin entrar a leer logs, cuanto dinero se esta perdiendo y por
     * cual razon -- util para decidir si la regla de elegibilidad
     * (ver docs/domain-model.md adenda de Fase 8) es demasiado
     * estricta en la practica.
     */
    private void forfeitCommission(User referrer, BigDecimal amountLost, String reason) {
        auditService.recordSystemAction("User", referrer.getId(), "REFERRAL_COMMISSION_FORFEITED_" + reason,
                null, amountLost);

        Counter.builder("luxtrox.referral.commission.forfeited")
                .description("Total en dolares perdido en comisiones de referido, por razon")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment(amountLost.doubleValue());
    }

    /**
     * Igual patron que el resto de los servicios: un fallo de Resend
     * nunca debe tumbar el pago de la comision, que ya quedo correcto
     * antes de llegar aqui.
     */
    private void notifyReferrerQuietly(User referrer, BigDecimal commission) {
        try {
            notificationEmailService.sendReferralBonusReceivedEmail(referrer, commission);
        } catch (Exception e) {
            auditService.recordSystemAction("User", referrer.getId(), "REFERRAL_EMAIL_NOTIFICATION_FAILED",
                    null, e.getMessage());
        }
    }
}
