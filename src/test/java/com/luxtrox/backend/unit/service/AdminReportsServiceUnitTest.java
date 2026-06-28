package com.luxtrox.backend.unit.service;

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
import com.luxtrox.backend.service.AdminReportsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportsServiceUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private CashbackTransactionRepository cashbackTransactionRepository;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private ReferralRepository referralRepository;
    @Mock private PurchaseRepository purchaseRepository;

    private AdminReportsService service() {
        return new AdminReportsService(userRepository, positionRepository, cashbackTransactionRepository,
                withdrawalRequestRepository, referralRepository, purchaseRepository);
    }

    @Test
    void getStats_mapsEachFieldFromItsOwnRepository() {
        when(userRepository.count()).thenReturn(120L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(95L);
        when(positionRepository.count()).thenReturn(80L);
        when(positionRepository.sumCapital()).thenReturn(new BigDecimal("88000.00"));
        when(cashbackTransactionRepository.sumAmount()).thenReturn(new BigDecimal("15400.50"));
        when(withdrawalRequestRepository.countByStatus(WithdrawalStatus.REQUESTED)).thenReturn(7L);
        when(withdrawalRequestRepository.sumAmountByStatus(WithdrawalStatus.REQUESTED)).thenReturn(new BigDecimal("3200.00"));
        when(referralRepository.count()).thenReturn(40L);
        when(purchaseRepository.sumConfirmedRevenueBetween(any(), any()))
                .thenReturn(new BigDecimal("5000.00"))  // mes actual
                .thenReturn(new BigDecimal("4000.00")); // mes anterior

        AdminStatsResponse stats = service().getStats();

        assertThat(stats.totalUsers()).isEqualTo(120L);
        assertThat(stats.activeUsers()).isEqualTo(95L);
        assertThat(stats.totalSeminars()).isEqualTo(80L);
        assertThat(stats.totalCapital()).isEqualByComparingTo("88000.00");
        assertThat(stats.totalCashbackPaid()).isEqualByComparingTo("15400.50");
        assertThat(stats.pendingWithdrawals()).isEqualTo(7L);
        assertThat(stats.pendingWithdrawalsAmount()).isEqualByComparingTo("3200.00");
        assertThat(stats.totalReferrals()).isEqualTo(40L);
        assertThat(stats.monthlyRevenue()).isEqualByComparingTo("5000.00");
        // (5000 - 4000) / 4000 * 100 = 25.00
        assertThat(stats.monthlyGrowth()).isEqualByComparingTo("25.00");
    }

    @Test
    void getStats_previousMonthZero_thisMonthPositive_growthIsHundredPercent() {
        stubMinimalCounts();
        when(purchaseRepository.sumConfirmedRevenueBetween(any(), any()))
                .thenReturn(new BigDecimal("1000.00"))
                .thenReturn(BigDecimal.ZERO);

        AdminStatsResponse stats = service().getStats();

        assertThat(stats.monthlyGrowth()).isEqualByComparingTo("100");
    }

    @Test
    void getStats_bothMonthsZero_growthIsZero_noDivisionByZero() {
        stubMinimalCounts();
        when(purchaseRepository.sumConfirmedRevenueBetween(any(), any()))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);

        AdminStatsResponse stats = service().getStats();

        assertThat(stats.monthlyGrowth()).isEqualByComparingTo("0");
    }

    @Test
    void getStats_revenueDeclined_growthIsNegative() {
        stubMinimalCounts();
        when(purchaseRepository.sumConfirmedRevenueBetween(any(), any()))
                .thenReturn(new BigDecimal("3000.00"))  // mes actual
                .thenReturn(new BigDecimal("4000.00")); // mes anterior (mas alto)

        AdminStatsResponse stats = service().getStats();

        // (3000 - 4000) / 4000 * 100 = -25.00
        assertThat(stats.monthlyGrowth()).isEqualByComparingTo("-25.00");
    }

    @Test
    void getRevenueChart_fillsMissingMonthsWithZero_keepsSixMonthWindow() {
        String thisMonth = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // La consulta solo devuelve UN mes con datos -- los otros 5 deben aparecer en 0, no faltar.
        when(purchaseRepository.sumRevenueByMonth(any()))
                .thenReturn(Collections.singletonList(new Object[]{thisMonth, new BigDecimal("777.00")}));

        List<ChartDataPointResponse> chart = service().getRevenueChart();

        assertThat(chart).hasSize(6);
        ChartDataPointResponse currentMonthPoint = chart.stream()
                .filter(p -> p.date().equals(thisMonth))
                .findFirst().orElseThrow();
        assertThat(currentMonthPoint.value()).isEqualByComparingTo("777.00");
        long zeroMonths = chart.stream().filter(p -> p.value().compareTo(BigDecimal.ZERO) == 0).count();
        assertThat(zeroMonths).isEqualTo(5);
    }

    @Test
    void getUsersGrowthChart_handlesCountAsAnyNumericType_notJustLong() {
        String thisMonth = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // Simula lo que el driver JDBC podria devolver para COUNT(*) -- Integer, no Long.
        when(userRepository.countNewUsersByMonth(any()))
                .thenReturn(Collections.singletonList(new Object[]{thisMonth, 9}));

        List<ChartDataPointResponse> chart = service().getUsersGrowthChart();

        ChartDataPointResponse currentMonthPoint = chart.stream()
                .filter(p -> p.date().equals(thisMonth))
                .findFirst().orElseThrow();
        assertThat(currentMonthPoint.value()).isEqualByComparingTo("9");
    }

    private void stubMinimalCounts() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.countByStatus(any())).thenReturn(0L);
        when(positionRepository.count()).thenReturn(0L);
        when(positionRepository.sumCapital()).thenReturn(BigDecimal.ZERO);
        when(cashbackTransactionRepository.sumAmount()).thenReturn(BigDecimal.ZERO);
        when(withdrawalRequestRepository.countByStatus(any())).thenReturn(0L);
        when(withdrawalRequestRepository.sumAmountByStatus(any())).thenReturn(BigDecimal.ZERO);
        when(referralRepository.count()).thenReturn(0L);
    }
}