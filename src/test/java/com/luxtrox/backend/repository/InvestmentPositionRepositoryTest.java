package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.*;
import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PositionStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Esta consulta es la base de la cascada de reasignacion de excedentes
 * del algoritmo de rendimiento mensual (docs/domain-model.md 4.1): "la
 * posicion ACTIVA mas reciente del usuario con saldo pendiente". Si
 * esto devuelve la posicion equivocada, la Fase 6 paga cashback al
 * lugar incorrecto -- por eso este test replica exactamente el
 * escenario que ya valide con SQL crudo antes de escribir este archivo.
 */
class InvestmentPositionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private InvestmentPositionRepository positionRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;

    private User createUser(String email, String referralCode) {
        Role role = roleRepository.findByName("USER").orElseThrow();
        return userRepository.save(new User("Test User", email, "+1", "hash", role, referralCode));
    }

    private InvestmentPosition createPosition(User user, BigDecimal cashbackPaid, BigDecimal cashbackRemaining,
                                                PositionStatus status, OffsetDateTime createdAt) {
        Purchase purchase = purchaseRepository.save(
                new Purchase(user, PlanType.DRIVER, 1, new BigDecimal("1100.00"), PaymentMethod.CRYPTO));

        InvestmentPosition position = new InvestmentPosition(
                user, purchase, new BigDecimal("1100.00"), new BigDecimal("3300.00"));
        position.setCashbackPaid(cashbackPaid);
        position.setCashbackRemaining(cashbackRemaining);
        position.setStatus(status);
        InvestmentPosition saved = positionRepository.saveAndFlush(position);

        // created_at tiene @Column(updatable = false) a proposito (es
        // inmutable en produccion), asi que un segundo save() de JPA
        // jamas escribiria un valor distinto en esa columna. Para que
        // este test pueda simular distintas fechas de creacion, se
        // necesita un UPDATE nativo que va por debajo de esa
        // restriccion de Hibernate directamente a la columna real.
        if (createdAt != null) {
            entityManager.createNativeQuery(
                            "UPDATE investment_positions SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.clear();
            saved = positionRepository.findById(saved.getId()).orElseThrow();
        }
        return saved;
    }

    @Test
    void findsOnlyActivePositionsWithRemainingBalance_orderedNewestFirst() {
        User user = createUser("posiciones@example.com", "POSREF001");
        OffsetDateTime now = OffsetDateTime.now();

        // Mismo escenario validado con SQL crudo: 1 COMPLETED (debe
        // quedar excluida) + 2 ACTIVE con distinto created_at.
        createPosition(user, new BigDecimal("3300.00"), BigDecimal.ZERO,
                PositionStatus.COMPLETED, now.minusDays(10));
        InvestmentPosition olderActive = createPosition(user, new BigDecimal("1000.00"), new BigDecimal("2300.00"),
                PositionStatus.ACTIVE, now.minusDays(5));
        InvestmentPosition newerActive = createPosition(user, new BigDecimal("500.00"), new BigDecimal("2800.00"),
                PositionStatus.ACTIVE, now.minusDays(1));

        List<InvestmentPosition> result = positionRepository
                .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                        user, PositionStatus.ACTIVE, BigDecimal.ZERO);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newerActive.getId()); // la mas reciente primero
        assertThat(result.get(1).getId()).isEqualTo(olderActive.getId());
    }

    @Test
    void excludesActivePositionsWithZeroRemainingBalance() {
        User user = createUser("sinremanente@example.com", "SINREM001");
        // ACTIVE pero sin saldo pendiente -- no deberia calificar para
        // recibir mas cashback aunque su status diga ACTIVE.
        createPosition(user, new BigDecimal("3300.00"), BigDecimal.ZERO,
                PositionStatus.ACTIVE, OffsetDateTime.now());

        List<InvestmentPosition> result = positionRepository
                .findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
                        user, PositionStatus.ACTIVE, BigDecimal.ZERO);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserAndStatusIgnoresOtherUsersPositions() {
        User userA = createUser("usera@example.com", "USERA001");
        User userB = createUser("userb@example.com", "USERB001");
        createPosition(userA, BigDecimal.ZERO, new BigDecimal("3300.00"), PositionStatus.ACTIVE, OffsetDateTime.now());
        createPosition(userB, BigDecimal.ZERO, new BigDecimal("3300.00"), PositionStatus.ACTIVE, OffsetDateTime.now());

        List<InvestmentPosition> result = positionRepository.findByUserAndStatus(userA, PositionStatus.ACTIVE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getId()).isEqualTo(userA.getId());
    }
}
