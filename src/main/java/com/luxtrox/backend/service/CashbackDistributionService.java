package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.MonthlyPerformance;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PositionStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.MonthlyPerformanceRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementa el algoritmo de distribucion de rendimiento mensual
 * descrito en docs/domain-model.md §4.1. Solo aplica a posiciones
 * DRIVER (InvestmentPosition) -- Zenith nunca participa de esto.
 *
 * Regla clave: nunca se paga de mas. El excedente que no cabe en
 * ninguna posicion del mismo usuario simplemente NO se paga (se
 * pierde para ese mes) -- a diferencia de las comisiones de referido
 * (ReferralService), que SIEMPRE pagan el monto completo al
 * available_balance aunque no haya posicion para "absorberlo".
 */
@Service
public class CashbackDistributionService {

    private final MonthlyPerformanceRepository performanceRepository;
    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public CashbackDistributionService(MonthlyPerformanceRepository performanceRepository,
                                        InvestmentPositionRepository positionRepository,
                                        CashbackTransactionRepository cashbackTransactionRepository,
                                        UserRepository userRepository,
                                        AuditService auditService) {
        this.performanceRepository = performanceRepository;
        this.positionRepository = positionRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MonthlyPerformance registerPerformance(Integer month, Integer year, BigDecimal percentage,
                                                    User registeredByAdmin) {
        if (performanceRepository.findByMonthAndYear(month, year).isPresent()) {
            throw new BusinessRuleException(
                    "Ya existe un rendimiento registrado para " + month + "/" + year);
        }
        return performanceRepository.save(new MonthlyPerformance(month, year, percentage, registeredByAdmin));
    }

    /**
     * Aplica un MonthlyPerformance ya registrado a todas las
     * posiciones ACTIVE de la plataforma. Idempotente: si ya se
     * aplico (appliedAt != null), no hace nada -- evita pagar doble
     * si el job se corre dos veces por error.
     */
    @Transactional
    public void distribute(UUID monthlyPerformanceId) {
        MonthlyPerformance performance = performanceRepository.findById(monthlyPerformanceId)
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPerformance no encontrado"));

        if (performance.getAppliedAt() != null) {
            return; // ya se aplico -- idempotencia
        }

        BigDecimal rate = performance.getPercentage().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);

        List<InvestmentPosition> activePositions =
                positionRepository.findByStatusOrderByCreatedAtAsc(PositionStatus.ACTIVE);

        for (InvestmentPosition position : activePositions) {
            applyToPosition(position, rate, performance, new HashSet<>());
        }

        performance.setAppliedAt(OffsetDateTime.now());
        performanceRepository.save(performance);
    }

    /**
     * Aplica el rendimiento a UNA posicion. Si excede su capacidad,
     * trunca y dispara la cascada de reasignacion hacia otras
     * posiciones ACTIVAS del MISMO usuario, de la mas reciente a la
     * mas antigua (ver docs/domain-model.md 4.1, supuesto #3
     * confirmado por el cliente).
     *
     * @param visited posiciones ya tocadas en ESTA cascada especifica
     *                -- evita que el excedente de A intente
     *                reasignarse de vuelta a A mismo si por algun
     *                motivo todavia aparece en una busqueda futura.
     */
    private void applyToPosition(InvestmentPosition position, BigDecimal rate,
                                  MonthlyPerformance performance, Set<UUID> visited) {
        if (position.getCashbackRemaining().compareTo(BigDecimal.ZERO) <= 0) {
            return; // ya esta en 0 (posiblemente completada por una cascada anterior en este mismo run)
        }
        visited.add(position.getId());

        BigDecimal nominal = position.getCapital().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        User user = position.getUser();

        if (nominal.compareTo(position.getCashbackRemaining()) <= 0) {
            // Cabe completo -- caso simple, sin truncamiento.
            applyPaymentToPosition(position, nominal);
            createTransaction(position, CashbackTransactionType.MONTHLY_PERFORMANCE, nominal, null, performance, null);
            creditAvailableBalance(user, nominal);
            return;
        }

        // No cabe completo -- truncar a lo que falta, y el resto cascadea.
        BigDecimal pagar = position.getCashbackRemaining();
        BigDecimal excedente = nominal.subtract(pagar);
        BigDecimal effectiveRate = pagar
                .divide(position.getCapital(), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        applyPaymentToPosition(position, pagar);
        createTransaction(position, CashbackTransactionType.MONTHLY_PERFORMANCE, pagar, effectiveRate, performance, null);
        creditAvailableBalance(user, pagar);

        cascadeExcess(user, position, excedente, performance, visited);
    }

    private void cascadeExcess(User user, InvestmentPosition source, BigDecimal excedente,
                                MonthlyPerformance performance, Set<UUID> visited) {
        BigDecimal remaining = excedente;

        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            Optional<InvestmentPosition> next = positionRepository
                    .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                            user, PositionStatus.ACTIVE, BigDecimal.ZERO)
                    .stream()
                    .filter(p -> !visited.contains(p.getId()))
                    .findFirst();

            if (next.isEmpty()) {
                // No hay donde reasignar -- el excedente se PIERDE,
                // a proposito (ver docs/domain-model.md 4.1, regla
                // clave: nunca se paga de mas).
                return;
            }

            InvestmentPosition destino = next.get();
            visited.add(destino.getId());

            BigDecimal aplicar = remaining.min(destino.getCashbackRemaining());
            applyPaymentToPosition(destino, aplicar);
            createTransaction(destino, CashbackTransactionType.MONTHLY_PERFORMANCE_REASSIGNED,
                    aplicar, null, performance, source);
            creditAvailableBalance(user, aplicar);

            remaining = remaining.subtract(aplicar);
        }
    }

    private void applyPaymentToPosition(InvestmentPosition position, BigDecimal amount) {
        BigDecimal oldPaid = position.getCashbackPaid();
        BigDecimal oldRemaining = position.getCashbackRemaining();

        position.setCashbackPaid(position.getCashbackPaid().add(amount));
        position.setCashbackRemaining(position.getCashbackRemaining().subtract(amount));

        if (position.getCashbackRemaining().compareTo(BigDecimal.ZERO) == 0) {
            position.setStatus(PositionStatus.COMPLETED);
            position.setCompletedAt(OffsetDateTime.now());
        }
        positionRepository.save(position);

        auditService.record(null, "InvestmentPosition", position.getId(), "MONTHLY_PERFORMANCE_APPLIED",
                new Object[]{oldPaid, oldRemaining},
                new Object[]{position.getCashbackPaid(), position.getCashbackRemaining()});
    }

    private void createTransaction(InvestmentPosition position, CashbackTransactionType type, BigDecimal amount,
                                    BigDecimal effectiveRate, MonthlyPerformance performance,
                                    InvestmentPosition reassignedFrom) {
        CashbackTransaction tx = new CashbackTransaction(position, type, amount);
        tx.setEffectiveRate(effectiveRate);
        tx.setSourcePerformance(performance);
        tx.setReassignedFromPosition(reassignedFrom);
        cashbackTransactionRepository.save(tx);
    }

    private void creditAvailableBalance(User user, BigDecimal amount) {
        BigDecimal oldBalance = user.getAvailableBalance();
        user.setAvailableBalance(user.getAvailableBalance().add(amount));
        userRepository.save(user);
        auditService.record(user, "User", user.getId(), "AVAILABLE_BALANCE_CREDITED", oldBalance,
                user.getAvailableBalance());
    }
}
