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

    /**
     * Solo para la grafica mensual (/cashback/monthly) -- excluye los
     * tipos sin sourcePerformance (REFERRAL_BONUS, REFERRAL_BONUS_DIRECT,
     * MANUAL_CREDIT) porque no tienen año/mes de rendimiento asociado
     * y romperían el GROUP BY de la grafica. El resumen y el historial
     * usan sumAllCashbackForUser/findAllCashbackForUser en su lugar.
     */
    private static final List<CashbackTransactionType> PERFORMANCE_TYPES =
            List.of(CashbackTransactionType.MONTHLY_PERFORMANCE, CashbackTransactionType.MONTHLY_PERFORMANCE_REASSIGNED);

    private static final int CHART_WINDOW_MONTHS = 6;

    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository transactionRepository;

    public CashbackQueryService(InvestmentPositionRepository positionRepository,
                                 CashbackTransactionRepository transactionRepository) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Resumen de cashback: incluye rendimiento mensual + comisiones de
     * referido (REFERRAL_BONUS / REFERRAL_BONUS_DIRECT) + creditos
     * manuales del admin (MANUAL_CREDIT) en totalGenerated. Antes solo
     * contaba MONTHLY_PERFORMANCE*, lo que dejaba las comisiones fuera
     * del "cashback generado" y del "cashback restante por pagar",
     * a pesar de que son dinero que la plataforma ya le ha pagado al
     * usuario.
     */
    @Transactional(readOnly = true)
    public CashbackSummaryResponse getSummary(User user) {
        BigDecimal totalGenerated = transactionRepository.sumAllCashbackForUser(user);
        BigDecimal targetFinal = positionRepository.sumTargetCashbackByUser(user);
        long seminarsCount = positionRepository.countByUser(user);

        return new CashbackSummaryResponse(
                totalGenerated,
                totalGenerated,
                user.getAvailableBalance(),
                targetFinal,
                progressPercentage(totalGenerated, targetFinal),
                seminarsCount
        );
    }

    /**
     * Historial de cashback: incluye TODOS los tipos (rendimiento +
     * comisiones + creditos manuales). Antes filtraba por CASHBACK_TYPES
     * = [MONTHLY_PERFORMANCE, MONTHLY_PERFORMANCE_REASSIGNED], dejando
     * las comisiones y creditos fuera del historial del usuario.
     */
    @Transactional(readOnly = true)
    public List<CashbackRecordResponse> getHistory(User user) {
        return transactionRepository.findAllCashbackForUser(user).stream()
                .map(CashbackRecordResponse::from)
                .toList();
    }

    /** Grafica mensual: solo rendimiento (tiene sourcePerformance para agrupar). */
    @Transactional(readOnly = true)
    public List<CashbackMonthlyResponse> getMonthlyChart(User user) {
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : transactionRepository.sumByPositionUserAndTypeInGroupedByMonth(user, PERFORMANCE_TYPES)) {
            Integer year = (Integer) row[0];
            Integer month = (Integer) row[1];
            byMonth.put(monthKey(year, month), toBigDecimal(row[2]));
        }
        return fillLastMonths(byMonth);
    }

    @Transactional(readOnly = true)
    public List<CashbackRecordResponse> getAllCashback() {
        return transactionRepository.findByTypeIn(PERFORMANCE_TYPES).stream()
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
