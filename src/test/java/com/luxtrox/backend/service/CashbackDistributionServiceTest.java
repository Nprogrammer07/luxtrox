package com.luxtrox.backend.service;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PositionStatus;
import com.luxtrox.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba el algoritmo de docs/domain-model.md §4.1 contra Postgres
 * real -- en particular la cascada de reasignacion de excedentes, que
 * es el codigo de mas riesgo de todo el proyecto (mueve dinero real
 * entre posiciones).
 */
class CashbackDistributionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CashbackDistributionService distributionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private InvestmentPositionRepository positionRepository;
    @Autowired
    private CashbackTransactionRepository transactionRepository;
    @Autowired
    private MonthlyPerformanceRepository performanceRepository;
    @Autowired
    private EntityManager entityManager;

    private User createUser(String email) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test", email, "+1", "hash", role, "REF" + email.hashCode()));
    }

    private User createAdmin(String email) {
        Role role = roleRepository.findByName("ADMIN").orElseThrow();
        return userRepository.save(new User("Admin", email, "+1", "hash", role, "ADM" + email.hashCode()));
    }

    /** Crea una posicion con cashback_paid/remaining EXACTOS, controlando created_at para ordenar la cascada. */
    private InvestmentPosition createPosition(User user, BigDecimal capital, BigDecimal cashbackPaid,
                                                OffsetDateTime createdAt) {
        Purchase purchase = purchaseRepository.save(
                new Purchase(user, PlanType.DRIVER, 1, capital, PaymentMethod.CRYPTO));
        BigDecimal target = capital.multiply(PlanPricing.CASHBACK_MULTIPLIER);

        InvestmentPosition position = new InvestmentPosition(user, purchase, capital, target);
        position.setCashbackPaid(cashbackPaid);
        position.setCashbackRemaining(target.subtract(cashbackPaid));
        InvestmentPosition saved = positionRepository.saveAndFlush(position);

        entityManager.createNativeQuery("UPDATE investment_positions SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
        return positionRepository.findById(saved.getId()).orElseThrow();
    }

    private MonthlyPerformance registerAndGetPerformance(int month, int year, String percentage, User admin) {
        return distributionService.registerPerformance(month, year, new BigDecimal(percentage), admin);
    }

    @Test
    void simpleCase_nominalFitsWithinRemaining_appliesFullyNoTruncation() {
        User user = createUser("simple@example.com");
        User admin = createAdmin("admin1@example.com");
        // capital 1100 -> target 3300. 10% nominal = 110, cabe perfecto en los 3300 disponibles.
        InvestmentPosition position = createPosition(user, new BigDecimal("1100.00"), BigDecimal.ZERO,
                OffsetDateTime.now());

        MonthlyPerformance perf = registerAndGetPerformance(7, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());

        InvestmentPosition refreshed = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshed.getCashbackPaid()).isEqualByComparingTo("110.00");
        assertThat(refreshed.getCashbackRemaining()).isEqualByComparingTo("3190.00");
        assertThat(refreshed.getStatus()).isEqualTo(PositionStatus.ACTIVE);

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshedUser.getAvailableBalance()).isEqualByComparingTo("110.00");
    }

    @Test
    void truncatesAndCascadesExcessToTheMostRecentOtherActivePosition() {
        User user = createUser("cascada@example.com");
        User admin = createAdmin("admin2@example.com");
        OffsetDateTime now = OffsetDateTime.now();

        // Posicion A: solo le faltan 50 para completarse -- el 10% de su
        // capital (1100*0.10=110) excede por mucho lo que le queda.
        InvestmentPosition posA = createPosition(user, new BigDecimal("1100.00"), new BigDecimal("3250.00"),
                now.minusDays(5)); // remaining = 50

        // Posicion B: mas reciente que A, con harto espacio -- debe
        // recibir el excedente de A (110 - 50 = 60).
        InvestmentPosition posB = createPosition(user, new BigDecimal("1100.00"), BigDecimal.ZERO,
                now.minusDays(1)); // remaining = 3300

        MonthlyPerformance perf = registerAndGetPerformance(8, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());

        InvestmentPosition refreshedA = positionRepository.findById(posA.getId()).orElseThrow();
        InvestmentPosition refreshedB = positionRepository.findById(posB.getId()).orElseThrow();

        // A se completa exactamente con lo que le faltaba, ni un centavo mas.
        assertThat(refreshedA.getCashbackRemaining()).isEqualByComparingTo("0.00");
        assertThat(refreshedA.getStatus()).isEqualTo(PositionStatus.COMPLETED);

        // B recibe: su propio 10% nominal (110) MAS el excedente cascadeado de A (60) = 170.
        assertThat(refreshedB.getCashbackPaid()).isEqualByComparingTo("170.00");

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        // El usuario recibe el TOTAL: 50 (A) + 110 (B nominal) + 60 (cascada) = 220.
        assertThat(refreshedUser.getAvailableBalance()).isEqualByComparingTo("220.00");
    }

    @Test
    void excessIsLostWhenNoOtherActivePositionExists_neverOverpays() {
        User user = createUser("sinotra@example.com");
        User admin = createAdmin("admin3@example.com");
        // Unica posicion del usuario, le faltan solo 20 -- el nominal (110) excede por mucho.
        InvestmentPosition position = createPosition(user, new BigDecimal("1100.00"), new BigDecimal("3280.00"),
                OffsetDateTime.now());

        MonthlyPerformance perf = registerAndGetPerformance(9, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());

        InvestmentPosition refreshed = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshed.getCashbackRemaining()).isEqualByComparingTo("0.00");
        assertThat(refreshed.getStatus()).isEqualTo(PositionStatus.COMPLETED);

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        // Solo recibe los 20 que cabian -- el excedente de 90 se PERDIO,
        // a proposito. Nunca se paga de mas (regla clave del algoritmo).
        assertThat(refreshedUser.getAvailableBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void recordsEffectiveRateOnlyWhenTruncated() {
        User user = createUser("rate@example.com");
        User admin = createAdmin("admin4@example.com");
        InvestmentPosition position = createPosition(user, new BigDecimal("1100.00"), new BigDecimal("3280.00"),
                OffsetDateTime.now()); // remaining = 20

        MonthlyPerformance perf = registerAndGetPerformance(10, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());

        var transactions = transactionRepository.findByPosition(
                positionRepository.findById(position.getId()).orElseThrow());

        assertThat(transactions).hasSize(1);
        // Se truncó a 20 de 110 nominal -> effectiveRate = 20/1100*100 = 1.82%, no el 10% nominal.
        assertThat(transactions.get(0).getEffectiveRate()).isEqualByComparingTo("1.82");
    }

    @Test
    void distributeIsIdempotent_runningTwiceDoesNotDoublePay() {
        User user = createUser("idempotente@example.com");
        User admin = createAdmin("admin5@example.com");
        InvestmentPosition position = createPosition(user, new BigDecimal("1100.00"), BigDecimal.ZERO,
                OffsetDateTime.now());

        MonthlyPerformance perf = registerAndGetPerformance(11, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());
        distributionService.distribute(perf.getId()); // segunda vez -- no debe hacer nada

        InvestmentPosition refreshed = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshed.getCashbackPaid()).isEqualByComparingTo("110.00"); // no 220

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshedUser.getAvailableBalance()).isEqualByComparingTo("110.00");
    }

    @Test
    void completedPositionsAreNeverTouchedByANewDistribution() {
        User user = createUser("completada@example.com");
        User admin = createAdmin("admin6@example.com");
        InvestmentPosition position = createPosition(user, new BigDecimal("1100.00"), new BigDecimal("3300.00"),
                OffsetDateTime.now()); // ya esta en su tope -- remaining = 0

        entityManager.createNativeQuery(
                "UPDATE investment_positions SET status = 'COMPLETED' WHERE id = :id")
                .setParameter("id", position.getId())
                .executeUpdate();
        entityManager.clear();

        MonthlyPerformance perf = registerAndGetPerformance(12, 2026, "10.00", admin);
        distributionService.distribute(perf.getId());

        InvestmentPosition refreshed = positionRepository.findById(position.getId()).orElseThrow();
        assertThat(refreshed.getCashbackPaid()).isEqualByComparingTo("3300.00"); // sin cambios

        User refreshedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshedUser.getAvailableBalance()).isEqualByComparingTo("0.00");
    }
}
