package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    List<Purchase> findByUser(User user);
    List<Purchase> findByStatus(PurchaseStatus status);

    /**
     * Elegibilidad para ganar comisiones de referido (ver
     * docs/domain-model.md 7.2): cualquier compra CONFIRMADA, sin
     * importar el plan (Driver o Zenith).
     */
    boolean existsByUserAndStatus(User user, PurchaseStatus status);

    /**
     * Para AdminReportsService.getStats() -- "monthlyRevenue" (mes
     * actual) y el denominador de "monthlyGrowth" (mes anterior).
     * [from, to) -- incluye from, excluye to.
     */
    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Purchase p " +
            "WHERE p.status = 'CONFIRMED' AND p.createdAt >= :from AND p.createdAt < :to")
    BigDecimal sumConfirmedRevenueBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /**
     * Ingreso confirmado por mes -- SQL nativo (no JPQL), mismo
     * motivo que UserRepository.countNewUsersByMonth: date_trunc es
     * especifico de Postgres, y este proyecto siempre corre contra
     * Postgres real. Solo incluye meses con al menos una compra
     * confirmada -- el llamador rellena los meses faltantes con 0.
     */
    @Query(value = "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, " +
            "COALESCE(SUM(total_amount), 0) AS revenue " +
            "FROM purchases WHERE status = 'CONFIRMED' AND created_at >= :since " +
            "GROUP BY date_trunc('month', created_at) ORDER BY date_trunc('month', created_at)",
            nativeQuery = true)
    List<Object[]> sumRevenueByMonth(@Param("since") OffsetDateTime since);
}
