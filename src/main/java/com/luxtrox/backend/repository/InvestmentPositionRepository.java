package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InvestmentPositionRepository extends JpaRepository<InvestmentPosition, UUID> {

    List<InvestmentPosition> findByStatus(PositionStatus status);

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
}
