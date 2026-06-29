package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvestmentPositionRepository extends JpaRepository<InvestmentPosition, UUID> {

    List<InvestmentPosition> findByStatus(PositionStatus status);

    /** Para PurchaseService.listMySeminars() -- "mis seminarios", todos los status, no solo activos. */
    List<InvestmentPosition> findByUser(User user);

    /**
     * Orden deterministico para el bucle PRINCIPAL del algoritmo de
     * distribucion mensual (docs/domain-model.md 4.1) -- no es
     * sensible a cual orden exacto se use (a diferencia de la
     * cascada de reasignacion, que SI exige "mas reciente primero"),
     * pero debe ser deterministico para que el job sea reproducible.
     */
    List<InvestmentPosition> findByStatusOrderByCreatedAtAsc(PositionStatus status);

    List<InvestmentPosition> findByUserAndStatus(User user, PositionStatus status);

    /**
     * El algoritmo de distribucion de rendimiento mensual y la
     * cascada de reasignacion de excedentes (docs/domain-model.md 4.1)
     * necesitan exactamente esto: las posiciones activas de un usuario
     * con saldo pendiente, de la mas reciente a la mas antigua.
     */
    List<InvestmentPosition> findByUserAndStatusAndCashbackRemainingGreaterThanOrderByCreatedAtDesc(
            User user, PositionStatus status, java.math.BigDecimal minCashbackRemaining);

    /**
     * Para AdminReportsService.getStats() -- "totalCapital" en el
     * dashboard de admin. Solo Driver genera InvestmentPosition
     * (Zenith genera ZenithLicense, sin participar del motor de
     * cashback -- ver PurchaseService), asi que esto es
     * deliberadamente capital de Driver unicamente.
     */
    @Query("SELECT COALESCE(SUM(p.capital), 0) FROM InvestmentPosition p")
    BigDecimal sumCapital();

    /** Para UserService -- "seminarsCount"/"totalInvested" del perfil de un usuario especifico. */
    long countByUser(User user);

    @Query("SELECT COALESCE(SUM(p.capital), 0) FROM InvestmentPosition p WHERE p.user = :user")
    BigDecimal sumCapitalByUser(@Param("user") User user);

    /** Para CashbackQueryService.getSummary() -- "targetFinal" (el cashback total al que un usuario tiene derecho, en sus posiciones Driver). */
    @Query("SELECT COALESCE(SUM(p.targetCashback), 0) FROM InvestmentPosition p WHERE p.user = :user")
    BigDecimal sumTargetCashbackByUser(@Param("user") User user);
}
