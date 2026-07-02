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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    private static final Logger log = LoggerFactory.getLogger(CashbackDistributionService.class);

    private final MonthlyPerformanceRepository performanceRepository;
    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public CashbackDistributionService(MonthlyPerformanceRepository performanceRepository,
                                        InvestmentPositionRepository positionRepository,
                                        CashbackTransactionRepository cashbackTransactionRepository,
                                        UserRepository userRepository,
                                        AuditService auditService,
                                        MeterRegistry meterRegistry) {
        this.performanceRepository = performanceRepository;
        this.positionRepository = positionRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
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
     * Scheduler diario a medianoche -- paga el rendimiento del mes
     * a las posiciones que HOY cumplen su aniversario mensual.
     *
     * Ejemplo: si un usuario compro el 15 de enero, su "cumpleaños"
     * es el dia 15 de cada mes. Si el admin registro 9% para julio,
     * este job pagara ese 9% a esa posicion el 15 de julio.
     *
     * Idempotente a nivel de posicion: si ya existe una transaccion
     * MONTHLY_PERFORMANCE para (posicion, performance), se salta.
     * Si no hay performance registrada para el mes actual, no hace nada.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void distributeAnniversariesForToday() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        int year  = today.getYear();
        int day   = today.getDayOfMonth();
        int lastDayOfMonth = today.lengthOfMonth();

        Optional<MonthlyPerformance> perfOpt = performanceRepository.findByMonthAndYear(month, year);
        if (perfOpt.isEmpty()) {
            log.info("[Scheduler] No hay rendimiento registrado para {}/{} -- skip", month, year);
            return;
        }
        MonthlyPerformance performance = perfOpt.get();
        BigDecimal rate = performance.getPercentage()
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);

        List<InvestmentPosition> positions = new java.util.ArrayList<>(
                positionRepository.findActiveByDayOfMonth(day));

        // Edge case: si hoy es el ultimo dia del mes, incluir posiciones cuyo
        // dia de nacimiento no existe en este mes (ej. nacidas el 29/30/31 en febrero).
        // Sin esto, una posicion creada el 31 de enero no cobra en febrero.
        if (day == lastDayOfMonth && day < 31) {
            for (int extraDay = day + 1; extraDay <= 31; extraDay++) {
                positions.addAll(positionRepository.findActiveByDayOfMonth(extraDay));
            }
            log.info("[Scheduler] Ultimo dia del mes -- incluyendo dias {}-31 del aniversario", day + 1);
        }

        log.info("[Scheduler] {} posiciones con aniversario hoy (dia {}), mes {}/{}",
                positions.size(), day, month, year);

        for (InvestmentPosition position : positions) {
            if (cashbackTransactionRepository
                    .existsByPositionAndSourcePerformanceAndType(
                            position, performance, CashbackTransactionType.MONTHLY_PERFORMANCE)) {
                log.debug("[Scheduler] Posicion {} ya pagada -- skip", position.getId());
                continue;
            }
            applyToPosition(position, rate, performance, new HashSet<>());
        }
    }

    /**
     * Distribucion manual -- paga a TODAS las posiciones activas que
     * aun no recibieron su pago de este MonthlyPerformance.
     * Util para: pruebas, correcciones y pagar posiciones cuyo dia de
     * aniversario ya paso sin que el scheduler corriera (ej. si el
     * backend estuvo caido ese dia).
     *
     * Idempotente: si una posicion ya fue pagada por el scheduler o
     * por una ejecucion anterior de este metodo, se la salta.
     */
    @Transactional
    public void distribute(UUID monthlyPerformanceId) {
        MonthlyPerformance performance = performanceRepository.findById(monthlyPerformanceId)
                .orElseThrow(() -> new ResourceNotFoundException("MonthlyPerformance no encontrado"));

        Timer.Sample sample = Timer.start(meterRegistry);
        BigDecimal rate = performance.getPercentage()
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);

        List<InvestmentPosition> activePositions =
                positionRepository.findByStatusOrderByCreatedAtAsc(PositionStatus.ACTIVE);

        int pagadas = 0;
        for (InvestmentPosition position : activePositions) {
            // Idempotencia SOLO por tipo MONTHLY_PERFORMANCE -- no por REASSIGNED.
            // Una posicion con transacciones REASSIGNED (excedente recibido de otras)
            // aun no ha recibido su propio pago nominal y NO debe saltarse.
            if (cashbackTransactionRepository
                    .existsByPositionAndSourcePerformanceAndType(
                            position, performance, CashbackTransactionType.MONTHLY_PERFORMANCE)) {
                continue;
            }
            applyToPosition(position, rate, performance, new HashSet<>());
            pagadas++;
        }

        log.info("[Manual] Distribucion {}/{}: {} posiciones pagadas",
                performance.getMonth(), performance.getYear(), pagadas);

        performance.setAppliedAt(OffsetDateTime.now());
        performanceRepository.save(performance);

        if (pagadas > 0) {
            sample.stop(Timer.builder("luxtrox.cashback.distribution")
                    .description("Duracion de una distribucion de rendimiento mensual completa")
                    .register(meterRegistry));
        }
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