package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PositionStatus;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import com.luxtrox.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementa el algoritmo de comisiones de referido descrito en
 * docs/domain-model.md §7.2 (adenda de Fase 6 -- reemplaza por
 * completo el bono fijo de $100 original de §4.2).
 *
 * Elegibilidad: cualquier compra CONFIRMADA del referente, Driver o
 * Zenith (no es necesario haber comprado Driver especificamente).
 *
 * Pago: la comision SIEMPRE se paga completa al available_balance del
 * referente. Si tiene una posicion Driver activa con saldo pendiente,
 * lo que cabe ahi tambien se contabiliza como avance de esa posicion
 * (cashback_paid/cashback_remaining); lo que no cabe (o el monto
 * completo si no hay posicion) se registra como
 * REFERRAL_BONUS_DIRECT, sin posicion asociada.
 */
@Service
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final AuditService auditService;
    private final NotificationEmailService notificationEmailService;

    public ReferralService(ReferralRepository referralRepository,
                            UserRepository userRepository,
                            PurchaseRepository purchaseRepository,
                            InvestmentPositionRepository positionRepository,
                            CashbackTransactionRepository cashbackTransactionRepository,
                            AuditService auditService,
                            NotificationEmailService notificationEmailService) {
        this.referralRepository = referralRepository;
        this.userRepository = userRepository;
        this.purchaseRepository = purchaseRepository;
        this.positionRepository = positionRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.auditService = auditService;
        this.notificationEmailService = notificationEmailService;
    }

    /**
     * Calcula la comision segun el plan comprado (ver §7.2):
     * Driver = 9% del precio, Zenith = 40% del precio.
     */
    public BigDecimal calculateCommission(Purchase referredPurchase) {
        BigDecimal rate = referredPurchase.getPlanType() == PlanType.DRIVER
                ? PlanPricing.DRIVER_REFERRAL_RATE
                : PlanPricing.ZENITH_REFERRAL_RATE;
        return referredPurchase.getTotalAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Disparador 1: la compra del REFERIDO se confirma. Si el
     * comprador tiene un referente (referredBy), marca su Referral
     * como calificado e intenta pagar de inmediato.
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

        // El bono es un evento UNICO por relacion de referido (ver
        // docs/domain-model.md 7.2) -- si el mismo referido hace una
        // segunda compra mas adelante (p.ej. Driver y luego Zenith),
        // eso no debe generar una segunda comision para el referente.
        if (referral.getStatus() == ReferralStatus.BONUS_PAID) {
            return;
        }

        if (referral.getQualifiedAt() == null) {
            referral.setQualifiedAt(OffsetDateTime.now());
            // Se guarda la compra exacta que califica -- es lo que
            // permite saber, incluso si el pago se reintenta despues
            // (disparador 2), si la comision es 9% o 40% sin tener
            // que adivinar "la mas reciente" del referido.
            referral.setTriggeringPurchase(confirmedPurchase);
        }

        attemptToPay(referral, confirmedPurchase);
    }

    /**
     * Disparador 2: la compra del REFERENTE se confirma (puede ser su
     * primera compra de cualquier tipo). Re-evalua todos sus
     * referidos que estaban esperando porque el todavia no calificaba.
     */
    @Transactional
    public void onReferrerPurchaseConfirmed(User referrer) {
        List<Referral> pending = referralRepository.findByReferrerAndStatus(
                referrer, ReferralStatus.QUALIFIED_AWAITING_REFERRER);

        for (Referral referral : pending) {
            Purchase triggeringPurchase = referral.getTriggeringPurchase();
            if (triggeringPurchase == null) {
                throw new IllegalStateException(
                        "Referral " + referral.getId() + " esta QUALIFIED_AWAITING_REFERRER "
                                + "sin triggering_purchase_id -- inconsistencia de datos");
            }
            attemptToPay(referral, triggeringPurchase);
        }
    }

    private void attemptToPay(Referral referral, Purchase referredPurchase) {
        User referrer = referral.getReferrer();
        boolean referrerIsEligible = purchaseRepository.existsByUserAndStatus(referrer, PurchaseStatus.CONFIRMED);

        if (!referrerIsEligible) {
            referral.setStatus(ReferralStatus.QUALIFIED_AWAITING_REFERRER);
            referralRepository.save(referral);
            return;
        }

        payBonus(referral, referredPurchase);
    }

    private void payBonus(Referral referral, Purchase referredPurchase) {
        User referrer = referral.getReferrer();
        BigDecimal commission = calculateCommission(referredPurchase);

        Optional<InvestmentPosition> destino = positionRepository
                .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                        referrer, PositionStatus.ACTIVE, BigDecimal.ZERO)
                .stream()
                .findFirst();

        BigDecimal remainder = commission;

        if (destino.isPresent()) {
            InvestmentPosition position = destino.get();
            BigDecimal applied = commission.min(position.getCashbackRemaining());

            BigDecimal oldPaid = position.getCashbackPaid();
            BigDecimal oldRemaining = position.getCashbackRemaining();

            position.setCashbackPaid(position.getCashbackPaid().add(applied));
            position.setCashbackRemaining(position.getCashbackRemaining().subtract(applied));
            if (position.getCashbackRemaining().compareTo(BigDecimal.ZERO) == 0) {
                position.setStatus(PositionStatus.COMPLETED);
                position.setCompletedAt(OffsetDateTime.now());
            }
            positionRepository.save(position);

            CashbackTransaction advanceTx = new CashbackTransaction(
                    position, CashbackTransactionType.REFERRAL_BONUS, applied);
            advanceTx.setSourceReferral(referral);
            cashbackTransactionRepository.save(advanceTx);

            auditService.record(referrer, "InvestmentPosition", position.getId(), "REFERRAL_BONUS_APPLIED",
                    new Object[]{oldPaid, oldRemaining},
                    new Object[]{position.getCashbackPaid(), position.getCashbackRemaining()});

            remainder = commission.subtract(applied);
            referral.setTargetPosition(position);
        }

        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            CashbackTransaction directTx = new CashbackTransaction(referrer, remainder);
            directTx.setSourceReferral(referral);
            cashbackTransactionRepository.save(directTx);
        }

        BigDecimal oldBalance = referrer.getAvailableBalance();
        referrer.setAvailableBalance(referrer.getAvailableBalance().add(commission));
        userRepository.save(referrer);

        auditService.record(referrer, "User", referrer.getId(), "REFERRAL_COMMISSION_PAID",
                oldBalance, referrer.getAvailableBalance());

        referral.setStatus(ReferralStatus.BONUS_PAID);
        referral.setBonusPaidAt(OffsetDateTime.now());
        referralRepository.save(referral);

        notifyReferrerQuietly(referrer, commission);
    }

    /**
     * Igual patron que PurchaseService/WithdrawalService: un fallo de
     * Resend nunca debe tumbar el pago de la comision, que ya quedo
     * correcto antes de llegar aqui.
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
