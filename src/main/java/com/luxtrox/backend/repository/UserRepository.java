package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByEmail(String email);
    long countByStatus(UserStatus status);

    /**
     * Usuarios nuevos por mes -- SQL nativo (no JPQL) porque
     * date_trunc es especifico de Postgres; este proyecto corre
     * siempre contra Postgres real (Testcontainers en tests,
     * Supabase en produccion), asi que no hay perdida real de
     * portabilidad. Devuelve [String mes 'YYYY-MM', Long conteo].
     * Solo incluye meses con AL MENOS un registro -- el llamador
     * (AdminReportsService) rellena los meses faltantes con 0.
     */
    @Query(value = "SELECT to_char(date_trunc('month', created_at), 'YYYY-MM') AS month, " +
            "COUNT(*) AS total " +
            "FROM users WHERE created_at >= :since " +
            "GROUP BY date_trunc('month', created_at) ORDER BY date_trunc('month', created_at)",
            nativeQuery = true)
    List<Object[]> countNewUsersByMonth(@Param("since") OffsetDateTime since);

    /**
     * Para UserService.listUsers() (admin) -- ambos filtros son
     * opcionales (NULL = no filtrar por ese criterio). Sin paginacion
     * real en el servidor a proposito, mismo criterio que el resto de
     * los listados de admin de este backend (ver AdminAlternativePaymentController
     * y similares): la base de usuarios de un negocio en etapa
     * temprana no justifica todavia la complejidad de Pageable/Page --
     * revisar si esto deja de ser cierto.
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR u.status = :status)")
    List<User> search(@Param("search") String search, @Param("status") UserStatus status);
}
