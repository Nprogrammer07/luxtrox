package com.luxtrox.backend.unit.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PositionStatus;
import com.luxtrox.backend.exception.BusinessRuleException;
import com.luxtrox.backend.exception.ResourceNotFoundException;
import com.luxtrox.backend.repository.CashbackTransactionRepository;
import com.luxtrox.backend.repository.InvestmentPositionRepository;
import com.luxtrox.backend.repository.MonthlyPerformanceRepository;
import com.luxtrox.backend.repository.UserRepository;
import com.luxtrox.backend.service.AuditService;
import com.luxtrox.backend.service.CashbackDistributionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unitario puro con Mockito. A proposito NO intenta repetir la cascada
 * de reasignacion de excedentes con mocks -- eso ya esta probado a
 * fondo contra Postgres real en CashbackDistributionServiceTest
 * (Testcontainers), y reimplementarlo aqui con stubs encadenados solo
 * agregaria fragilidad sin valor nuevo. Aqui se cubre lo que ESE test
 * no cubre: idempotencia, validaciones de entrada, y el caso simple
 * (sin truncamiento) para confirmar que el cableado de dependencias
 * es correcto.
 */
@ExtendWith(MockitoExtension.class)
class CashbackDistributionServiceUnitTest {

    @Mock private MonthlyPerformanceRepository performanceRepository;
    @Mock private InvestmentPositionRepository positionRepository;
    @Mock private CashbackTransactionRepository cashbackTransactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private CashbackDistributionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new CashbackDistributionService(performanceRepository, positionRepository,
                cashbackTransactionRepository, userRepository, auditService);

        Role role = new Role("USER", "Usuario estandar");
        user = new User("Carlos", "carlos@example.com", "+1", "hash", role, "CARLOS01");
        setId(user, UUID.randomUUID());
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

    // ---------- registerPerformance() ----------

    @Test
    void registerPerformance_happyPath_savesIt() {
        when(performanceRepository.findByMonthAndYear(7, 2026)).thenReturn(Optional.empty());
        when(performanceRepository.save(any(MonthlyPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MonthlyPerformance result = service.registerPerformance(7, 2026, new BigDecimal("10.00"), user);

        assertThat(result.getMonth()).isEqualTo(7);
        assertThat(result.getPercentage()).isEqualByComparingTo("10.00");
    }

    @Test
    void registerPerformance_duplicateMonthYear_throwsAndNeverSaves() {
        when(performanceRepository.findByMonthAndYear(7, 2026))
                .thenReturn(Optional.of(new MonthlyPerformance(7, 2026, new BigDecimal("5.00"), user)));

        assertThrows(BusinessRuleException.class,
                () -> service.registerPerformance(7, 2026, new BigDecimal("10.00"), user));

        verify(performanceRepository, never()).save(any());
    }

    // ---------- distribute() ----------

    @Test
    void distribute_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(performanceRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.distribute(id));
    }

    @Test
    void distribute_alreadyApplied_isIdempotentAndTouchesNoPositions() {
        MonthlyPerformance performance = new MonthlyPerformance(7, 2026, new BigDecimal("10.00"), user);
        performance.setAppliedAt(OffsetDateTime.now().minusDays(1)); // ya se aplico antes
        UUID id = UUID.randomUUID();
        setId(performance, id);
        when(performanceRepository.findById(id)).thenReturn(Optional.of(performance));

        service.distribute(id);

        verifyNoInteractions(positionRepository, cashbackTransactionRepository, userRepository);
        verify(performanceRepository, never()).save(any());
    }

    @Test
    void distribute_simpleCase_noTruncation_appliesNominalAndMarksApplied() {
        MonthlyPerformance performance = new MonthlyPerformance(7, 2026, new BigDecimal("10.00"), user);
        UUID performanceId = UUID.randomUUID();
        setId(performance, performanceId);

        InvestmentPosition position = activePosition(user, new BigDecimal("1000.00"), new BigDecimal("3000.00"));
        // Posicion recien creada (remaining = target -- nada pagado todavia).
        // nominal = 1000 * 10% = 100, cabe perfecto dentro de 3000 de remaining -- sin truncamiento.

        when(performanceRepository.findById(performanceId)).thenReturn(Optional.of(performance));
        when(positionRepository.findByStatusOrderByCreatedAtAsc(PositionStatus.ACTIVE))
                .thenReturn(List.of(position));

        service.distribute(performanceId);

        ArgumentCaptor<InvestmentPosition> positionCaptor = ArgumentCaptor.forClass(InvestmentPosition.class);
        verify(positionRepository).save(positionCaptor.capture());
        assertThat(positionCaptor.getValue().getCashbackPaid()).isEqualByComparingTo("100.00");
        assertThat(positionCaptor.getValue().getCashbackRemaining()).isEqualByComparingTo("2900.00");
        assertThat(positionCaptor.getValue().getStatus()).isEqualTo(PositionStatus.ACTIVE); // no se completo

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvailableBalance()).isEqualByComparingTo("100.00");

        verify(cashbackTransactionRepository).save(any(CashbackTransaction.class));

        // La cascada NUNCA debio dispararse -- no hubo truncamiento.
        verify(positionRepository, never())
                .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(any(), any(), any());

        ArgumentCaptor<MonthlyPerformance> perfCaptor = ArgumentCaptor.forClass(MonthlyPerformance.class);
        verify(performanceRepository).save(perfCaptor.capture());
        assertThat(perfCaptor.getValue().getAppliedAt()).isNotNull();
    }

    @Test
    void distribute_noActivePositions_stillMarksPerformanceAsApplied() {
        MonthlyPerformance performance = new MonthlyPerformance(7, 2026, new BigDecimal("10.00"), user);
        UUID performanceId = UUID.randomUUID();
        setId(performance, performanceId);

        when(performanceRepository.findById(performanceId)).thenReturn(Optional.of(performance));
        when(positionRepository.findByStatusOrderByCreatedAtAsc(PositionStatus.ACTIVE))
                .thenReturn(List.of());

        service.distribute(performanceId);

        verifyNoInteractions(cashbackTransactionRepository, userRepository);
        ArgumentCaptor<MonthlyPerformance> perfCaptor = ArgumentCaptor.forClass(MonthlyPerformance.class);
        verify(performanceRepository).save(perfCaptor.capture());
        assertThat(perfCaptor.getValue().getAppliedAt()).isNotNull();
    }

    private InvestmentPosition activePosition(User owner, BigDecimal capital, BigDecimal remaining) {
        Purchase purchase = new Purchase(owner, com.luxtrox.backend.entity.enums.PlanType.DRIVER, 1,
                capital, com.luxtrox.backend.entity.enums.PaymentMethod.CRYPTO);
        InvestmentPosition position = new InvestmentPosition(owner, purchase, capital,
                capital.multiply(new BigDecimal("3.0")));
        position.setCashbackRemaining(remaining);
        position.setCashbackPaid(position.getTargetCashback().subtract(remaining));
        setId(position, UUID.randomUUID());
        return position;
    }
}
