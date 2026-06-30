package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.dto.cashback.CashbackMonthlyResponse;
import com.luxtrox.backend.dto.cashback.CashbackRecordResponse;
import com.luxtrox.backend.dto.cashback.CashbackSummaryResponse;
import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.service.CashbackQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashbackQueryServiceUnitTest {

    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private CashbackTransactionRepository transactionRepository;

    private CashbackQueryService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CashbackQueryService(positionRepository, transactionRepository);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());
        user.setAvailableBalance(new BigDecimal("500.00"));

        lenient().when(transactionRepository.sumAllCashbackForUser(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(positionRepository.sumTargetCashbackByUser(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(positionRepository.countByUser(any())).thenReturn(0L);
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private InvestmentPosition positionFor(User owner) {
        Purchase purchase = new Purchase(owner, PlanType.DRIVER, 1, new BigDecimal("1099.00"), PaymentMethod.CRYPTO);
        setId(purchase, UUID.randomUUID());
        InvestmentPosition position = new InvestmentPosition(owner, purchase, new BigDecimal("1099.00"), new BigDecimal("2198.00"));
        setId(position, UUID.randomUUID());
        return position;
    }

    private CashbackTransaction transactionFor(InvestmentPosition position, BigDecimal amount, int year, int month) {
        CashbackTransaction tx = new CashbackTransaction(position, CashbackTransactionType.MONTHLY_PERFORMANCE, amount);
        setId(tx, UUID.randomUUID());
        MonthlyPerformance performance = new MonthlyPerformance(month, year, new BigDecimal("10.00"), user);
        setId(performance, UUID.randomUUID());
        tx.setSourcePerformance(performance);
        return tx;
    }

    // ---------- getSummary() ----------

    @Test
    void getSummary_includesPerformanceAndCommissionsAndManualCredits_inTotalGenerated() {
        when(transactionRepository.sumAllCashbackForUser(user)).thenReturn(new BigDecimal("500.00"));
        when(positionRepository.sumTargetCashbackByUser(user)).thenReturn(new BigDecimal("2000.00"));
        when(positionRepository.countByUser(user)).thenReturn(2L);

        CashbackSummaryResponse summary = service.getSummary(user);

        assertThat(summary.totalGenerated()).isEqualByComparingTo("500.00");
        assertThat(summary.totalReceived()).isEqualByComparingTo("500.00");
        assertThat(summary.available()).isEqualByComparingTo("500.00"); // user.availableBalance
        assertThat(summary.targetFinal()).isEqualByComparingTo("2000.00");
        assertThat(summary.seminarsCount()).isEqualTo(2L);
        // 500/2000 * 100 = 25.00
        assertThat(summary.progress()).isEqualByComparingTo("25.00");
    }

    @Test
    void getSummary_noPositionsYet_progressIsZero_noDivisionByZero() {
        CashbackSummaryResponse summary = service.getSummary(user);

        assertThat(summary.progress()).isEqualByComparingTo("0");
    }

    // ---------- getHistory() ----------

    @Test
    void getHistory_mapsPositionTransactionsToRecords() {
        InvestmentPosition position = positionFor(user);
        CashbackTransaction tx = transactionFor(position, new BigDecimal("109.90"), 2026, 3);
        when(transactionRepository.findAllCashbackForUser(user)).thenReturn(List.of(tx));

        List<CashbackRecordResponse> history = service.getHistory(user);

        assertThat(history).hasSize(1);
        CashbackRecordResponse record = history.get(0);
        assertThat(record.userId()).isEqualTo(user.getId());
        assertThat(record.seminarId()).isEqualTo(position.getId());
        assertThat(record.amount()).isEqualByComparingTo("109.90");
        assertThat(record.month()).isEqualTo(3);
        assertThat(record.year()).isEqualTo(2026);
        assertThat(record.status()).isEqualTo("PAID");
    }

    @Test
    void getHistory_manualCreditWithNoPosition_seminarIdIsNull_userIdFromDirectUser() {
        CashbackTransaction tx = new CashbackTransaction(user, CashbackTransactionType.MANUAL_CREDIT, new BigDecimal("50.00"));
        setId(tx, UUID.randomUUID());
        when(transactionRepository.findAllCashbackForUser(user)).thenReturn(List.of(tx));

        List<CashbackRecordResponse> history = service.getHistory(user);

        assertThat(history).hasSize(1);
        CashbackRecordResponse record = history.get(0);
        assertThat(record.userId()).isEqualTo(user.getId());
        assertThat(record.seminarId()).isNull();  // no position
        assertThat(record.amount()).isEqualByComparingTo("50.00");
        assertThat(record.month()).isNull();       // no sourcePerformance
        assertThat(record.year()).isNull();
    }

    // ---------- getMonthlyChart() ----------

    @Test
    void getMonthlyChart_fillsMissingMonthsWithZero_keepsSixMonthWindow() {
        LocalDate now = LocalDate.now();
        when(transactionRepository.sumByPositionUserAndTypeInGroupedByMonth(eq(user), anyTypes()))
                .thenReturn(Collections.singletonList(new Object[]{now.getYear(), now.getMonthValue(), new BigDecimal("250.00")}));

        List<CashbackMonthlyResponse> chart = service.getMonthlyChart(user);

        assertThat(chart).hasSize(6);
        String thisMonthKey = String.format("%04d-%02d", now.getYear(), now.getMonthValue());
        CashbackMonthlyResponse currentMonth = chart.stream()
                .filter(p -> p.month().equals(thisMonthKey)).findFirst().orElseThrow();
        assertThat(currentMonth.generated()).isEqualByComparingTo("250.00");
        assertThat(currentMonth.received()).isEqualByComparingTo("250.00");
        long zeroMonths = chart.stream().filter(p -> p.generated().compareTo(BigDecimal.ZERO) == 0).count();
        assertThat(zeroMonths).isEqualTo(5);
    }

    // ---------- getAllCashback() (admin) ----------

    @Test
    void getAllCashback_mapsAllTransactionsRegardlessOfUser() {
        InvestmentPosition position = positionFor(user);
        CashbackTransaction tx = transactionFor(position, new BigDecimal("50.00"), 2026, 1);
        when(transactionRepository.findByTypeIn(anyTypes())).thenReturn(List.of(tx));

        List<CashbackRecordResponse> all = service.getAllCashback();

        assertThat(all).hasSize(1);
        assertThat(all.get(0).userId()).isEqualTo(user.getId());
    }

    @SuppressWarnings("unchecked")
    private List<CashbackTransactionType> anyTypes() {
        return any(List.class);
    }
}
