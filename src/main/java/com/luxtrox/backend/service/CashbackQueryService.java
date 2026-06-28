package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.cashback.CashbackMonthlyResponse;
import com.luxtrox.backend.dto.cashback.CashbackRecordResponse;
import com.luxtrox.backend.dto.cashback.CashbackSummaryResponse;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de cashback para el usuario (resumen, historial, grafica
 * mensual) y para el admin (listado completo) -- pedidas por el
 * frontend (Next.js), que ya tenia /cashback/summary, /cashback/history,
 * /cashback/monthly y /admin/cashback definidos de forma especulativa
 * antes de que este backend existiera. CashbackController solo tenia
 * /cashback/positions/{id}/transactions; AdminCashbackController solo
 * tenia los 2 endpoints para DISPARAR la distribucion mensual, nada
 * para CONSULTARLA.
 *
 * Todos los metodos son @Transactional(readOnly=true) -- no porque
 * muten nada, sino porque CashbackRecordResponse.from() lee
 * tx.getPosition().getUser() (ambos FetchType.LAZY): el mapeo a DTO
 * tiene que correr DENTRO de la misma transaccion que cargo la
 * entidad, no despues (ver el mismo problema, ya resuelto, en
 * UserService y AlternativePaymentService/controllers).
 *
 * Las unicas dos llamadas que NO necesitan esa proteccion son las que
 * usan `user` solo como filtro de ID en una consulta (countByUser,
 * sumCapitalByUser, etc.) -- eso nunca requiere que `user` este
 * "adjunto" a una sesion, solo necesita su id.
 */
@Service
public class CashbackQueryService {

    /** Tipos que representan cashback de POSICION -- excluye REFERRAL_BONUS* a proposito (dominio separado en el frontend). */
    private static final List<CashbackTransactionType> CASHBACK_TYPES =
            List.of(CashbackTransactionType.MONTHLY_PERFORMANCE, CashbackTransactionType.MONTHLY_PERFORMANCE_REASSIGNED);

    private static final int CHART_WINDOW_MONTHS = 6;

    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository transactionRepository;

    public CashbackQueryService(InvestmentPositionRepository positionRepository,
                                 CashbackTransactionRepository transactionRepository) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public CashbackSummaryResponse getSummary(User user) {
        BigDecimal totalReceived = transactionRepository.sumByPositionUserAndTypeIn(user, CASHBACK_TYPES);
        BigDecimal targetFinal = positionRepository.sumTargetCashbackByUser(user);
        long seminarsCount = positionRepository.countByUser(user);

        return new CashbackSummaryResponse(
                totalReceived,        // totalGenerated == totalReceived en este backend, ver javadoc de la clase DTO
                totalReceived,
                user.getAvailableBalance(),
                targetFinal,
                progressPercentage(totalReceived, targetFinal),
                seminarsCount
        );
    }

    @Transactional(readOnly = true)
    public List<CashbackRecordResponse> getHistory(User user) {
        return transactionRepository.findByPositionUserAndTypeIn(user, CASHBACK_TYPES).stream()
                .map(CashbackRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CashbackMonthlyResponse> getMonthlyChart(User user) {
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : transactionRepository.sumByPositionUserAndTypeInGroupedByMonth(user, CASHBACK_TYPES)) {
            Integer year = (Integer) row[0];
            Integer month = (Integer) row[1];
            byMonth.put(monthKey(year, month), toBigDecimal(row[2]));
        }
        return fillLastMonths(byMonth);
    }

    @Transactional(readOnly = true)
    public List<CashbackRecordResponse> getAllCashback() {
        return transactionRepository.findByTypeIn(CASHBACK_TYPES).stream()
                .map(CashbackRecordResponse::from)
                .toList();
    }

    private BigDecimal progressPercentage(BigDecimal received, BigDecimal target) {
        if (target.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return received.divide(target, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String monthKey(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(((Number) value).toString());
    }

    /** Mismo patron de relleno de meses que AdminReportsService -- ver ese comentario para el porque. */
    private List<CashbackMonthlyResponse> fillLastMonths(Map<String, BigDecimal> byMonth) {
        LocalDate cursor = LocalDate.now().withDayOfMonth(1).minusMonths(CHART_WINDOW_MONTHS - 1L);
        List<CashbackMonthlyResponse> result = new ArrayList<>();
        for (int i = 0; i < CHART_WINDOW_MONTHS; i++) {
            String key = monthKey(cursor.getYear(), cursor.getMonthValue());
            BigDecimal value = byMonth.getOrDefault(key, BigDecimal.ZERO);
            result.add(new CashbackMonthlyResponse(key, value, value));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }
}
