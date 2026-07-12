package com.luxtrox.backend.service;

import com.luxtrox.backend.dto.adminreports.AdminStatsResponse;
import com.luxtrox.backend.dto.adminreports.ChartDataPointResponse;
import com.luxtrox.backend.entity.enums.UserStatus;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
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
 * Estadisticas del panel de admin. Tras eliminar el modulo Driver,
 * los campos de posiciones (totalPositions, totalCapital) se reportan
 * como 0 -- el DTO AdminStatsResponse mantiene su forma para no romper
 * el frontend, pero esos valores ya no aplican a Zenith/Plus.
 *
 * totalCashbackPaid ahora es SUM de comisiones de referido + creditos
 * manuales (los dos unicos tipos que quedan en cashback_transactions).
 */
@Service
public class AdminReportsService {

    private static final int CHART_WINDOW_MONTHS = 6;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final CashbackTransactionRepository cashbackTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final ReferralRepository referralRepository;
    private final PurchaseRepository purchaseRepository;

    public AdminReportsService(UserRepository userRepository,
                                CashbackTransactionRepository cashbackTransactionRepository,
                                WithdrawalRequestRepository withdrawalRequestRepository,
                                ReferralRepository referralRepository,
                                PurchaseRepository purchaseRepository) {
        this.userRepository = userRepository;
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
                0L,                                   // totalPositions -- Driver eliminado
                BigDecimal.ZERO,                      // totalCapital -- Driver eliminado
                cashbackTransactionRepository.sumAllCommissionsAndCredits(),
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
