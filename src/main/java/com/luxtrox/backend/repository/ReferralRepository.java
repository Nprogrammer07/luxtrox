package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {
    Optional<Referral> findByReferred(User referred);
    List<Referral> findByReferrerAndStatus(User referrer, ReferralStatus status);

    /**
     * Referidos nuevos por mes (sin importar status) -- para
     * AdminReportsService.getReferralsChart(). SQL nativo, mismo
     * motivo que las demas consultas agrupadas por mes en este
     * proyecto (date_trunc es de Postgres, y este proyecto siempre
     * corre contra Postgres real).
     */
    @Query(value = "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, " +
            "COUNT(*) AS total " +
            "FROM referrals WHERE created_at >= :since " +
            "GROUP BY date_trunc('month', created_at) ORDER BY date_trunc('month', created_at)",
            nativeQuery = true)
    List<Object[]> countByMonth(@Param("since") OffsetDateTime since);
}
