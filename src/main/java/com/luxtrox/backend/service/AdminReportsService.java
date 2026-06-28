package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.adminreports.AdminStatsResponse;
import com.luxtrox.backend.dto.adminreports.ChartDataPointResponse;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.PurchaseRepository;
import com.luxtrox.backend.repository.ReferralRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.repository.WithdrawalRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard agregado de admin -- pedido por el frontend (Next.js),
 * que ya tenia el tipo `AdminStats` y 3 endpoints de graficas
 * definidos de forma especulativa antes de que este backend
 * existiera. Aqui el mapeo de cada campo contra las tablas reales:
 *
 * - totalUsers / activeUsers: conteo de users, activeUsers = status ACTIVE.
 * - totalSeminars: "seminario" en el frontend = un paquete Driver
 *   comprado = una InvestmentPosition en este backend. Zenith NO
 *   cuenta -- genera ZenithLicense, no participa del motor de
 *   cashback (ver PurchaseService), asi que no es un "seminario" en
 *   ese sentido.
 * - totalCapital: SUM(capital) de InvestmentPosition -- por la misma
 *   razon, es capital de Driver unicamente.
 * - totalCashbackPaid: SUM(amount) de CashbackTransaction -- los 4
 *   tipos representan dinero realmente repartido (no existe un tipo
 *   "FORFEITED": los montos no aplicados nunca generan fila aqui).
 * - pendingWithdrawals(Amount): WithdrawalRequest con status
 *   REQUESTED (el nombre "pending" del frontend es REQUESTED aqui).
 * - totalReferrals: COUNT(*) de Referral, sin filtrar por status --
 *   el "total" mas literal posible.
 * - monthlyRevenue: SUM(total_amount) de compras CONFIRMADAS del mes
 *   calendario actual.
 * - monthlyGrowth: cambio porcentual de monthlyRevenue contra el mes
 *   calendario anterior. Si el mes anterior fue 0: 100% si este mes
 *   tiene ingreso, 0% si tambien fue 0 (evita dividir por cero).
 *
 * Las 3 graficas (revenue/users/referrals) cubren los ultimos 6
 * meses calendario, incluyendo el actual -- ventana fija, sin
 * parametro porque el tipo ChartDataPoint del frontend no define una.
 * Los meses sin actividad se rellenan con 0 explicitamente: una
 * consulta GROUP BY normal simplemente OMITE esos meses, lo que
 * dejaria huecos en la grafica del frontend en vez de mostrar un
 * punto en cero.
 */
@Service
public class AdminReportsService {

    private static final int CHART_WINDOW_MONTHS = 6;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final InvestmentPositionRepository positionRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final ReferralRepository referralRepository;
    private final PurchaseRepository purchaseRepository;

    public AdminReportsService(UserRepository userRepository,
                                InvestmentPositionRepository positionRepository,
                                CashbackTransactionRepository cashbackTransactionRepository,
                                WithdrawalRequestRepository withdrawalRequestRepository,
                                ReferralRepository referralRepository,
                                PurchaseRepository purchaseRepository) {
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.cashbackTransactionRepository = cashbackTransactionRepository;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.referralRepository = referralRepository;
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfThisMonth = startOfMonth(now);
        OffsetDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        BigDecimal monthlyRevenue = purchaseRepository.sumConfirmedRevenueBetween(startOfThisMonth, now);
        BigDecimal lastMonthRevenue = purchaseRepository.sumConfirmedRevenueBetween(startOfLastMonth, startOfThisMonth);

        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                positionRepository.count(),
                positionRepository.sumCapital(),
                cashbackTransactionRepository.sumAmount(),
                withdrawalRequestRepository.countByStatus(WithdrawalStatus.REQUESTED),
                withdrawalRequestRepository.sumAmountByStatus(WithdrawalStatus.REQUESTED),
                referralRepository.count(),
                monthlyRevenue,
                growthPercentage(lastMonthRevenue, monthlyRevenue)
        );
    }

    @Transactional(readOnly = true)
    public List<ChartDataPointResponse> getRevenueChart() {
        OffsetDateTime since = startOfMonth(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(CHART_WINDOW_MONTHS - 1L);
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : purchaseRepository.sumRevenueByMonth(since)) {
            byMonth.put((String) row[0], toBigDecimal(row[1]));
        }
        return fillLastMonths(byMonth, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<ChartDataPointResponse> getUsersGrowthChart() {
        OffsetDateTime since = startOfMonth(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(CHART_WINDOW_MONTHS - 1L);
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : userRepository.countNewUsersByMonth(since)) {
            byMonth.put((String) row[0], toBigDecimal(row[1]));
        }
        return fillLastMonths(byMonth, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<ChartDataPointResponse> getReferralsChart() {
        OffsetDateTime since = startOfMonth(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(CHART_WINDOW_MONTHS - 1L);
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Object[] row : referralRepository.countByMonth(since)) {
            byMonth.put((String) row[0], toBigDecimal(row[1]));
        }
        return fillLastMonths(byMonth, BigDecimal.ZERO);
    }

    /**
     * Las columnas de conteo/suma de consultas nativas pueden volver
     * como Long, Integer o BigInteger segun el driver JDBC -- castear
     * directo a un tipo especifico arriesga ClassCastException. Number
     * es la unica superclase comun garantizada para cualquier numero
     * que JDBC devuelva.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(((Number) value).toString());
    }

    private OffsetDateTime startOfMonth(OffsetDateTime dateTime) {
        return dateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private BigDecimal growthPercentage(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100") : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Genera los ultimos CHART_WINDOW_MONTHS meses (incluyendo el actual), rellenando con defaultValue donde la consulta no trajo nada. */
    private List<ChartDataPointResponse> fillLastMonths(Map<String, BigDecimal> byMonth, BigDecimal defaultValue) {
        OffsetDateTime cursor = startOfMonth(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(CHART_WINDOW_MONTHS - 1L);
        List<ChartDataPointResponse> result = new ArrayList<>();
        for (int i = 0; i < CHART_WINDOW_MONTHS; i++) {
            String key = cursor.format(MONTH_FORMAT);
            result.add(new ChartDataPointResponse(key, byMonth.getOrDefault(key, defaultValue)));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }
}
