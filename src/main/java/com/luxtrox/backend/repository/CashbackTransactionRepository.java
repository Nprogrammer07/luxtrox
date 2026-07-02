package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.MonthlyPerformance;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, UUID> {
    List<CashbackTransaction> findByPosition(InvestmentPosition position);

    /** Solo encuentra las de tipo REFERRAL_BONUS_DIRECT (las unicas con user_id propio). */
    List<CashbackTransaction> findByUser(User user);

    /**
     * Para AdminReportsService.getStats() -- "totalCashbackPaid". Los
     * 4 valores de CashbackTransactionType representan dinero
     * realmente repartido (no hay un tipo "FORFEITED" -- las
     * cantidades no aplicadas nunca generan una fila aqui), asi que
     * es una suma simple sin filtro de tipo.
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c")
    BigDecimal sumAmount();

    /**
     * Para CashbackQueryService -- "historial de cashback" de UN
     * usuario (CashbackController.history()). Va por
     * c.position.user, NO c.user: ese campo solo se llena en
     * REFERRAL_BONUS_DIRECT (ver comentario de findByUser arriba) --
     * MONTHLY_PERFORMANCE/REASSIGNED tienen su usuario unicamente a
     * traves de la posicion. types se pasa explicito para EXCLUIR los
     * tipos de referido (son un dominio separado en el frontend, su
     * propio servicio/tipo).
     */
    @Query("SELECT c FROM CashbackTransaction c WHERE c.position.user = :user AND c.type IN :types " +
            "ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findByPositionUserAndTypeIn(@Param("user") User user,
                                                            @Param("types") List<CashbackTransactionType> types);

    /** Para CashbackDistributionService.distributeForAnniversaries() */
    @Query("SELECT p FROM InvestmentPosition p WHERE p.status = 'ACTIVE' " +
            "AND EXTRACT(DAY FROM p.createdAt) = :day")
    List<InvestmentPosition> findActiveByDayOfMonth(@Param("day") int day);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "WHERE c.position.user = :user AND c.type IN :types")
    BigDecimal sumByPositionUserAndTypeIn(@Param("user") User user,
                                           @Param("types") List<CashbackTransactionType> types);

    /**
     * Cashback por mes de UN usuario, para la grafica de
     * /cashback/monthly -- agrupado por el año/mes de
     * sourcePerformance (siempre presente tanto en MONTHLY_PERFORMANCE
     * como en MONTHLY_PERFORMANCE_REASSIGNED, ver CashbackDistributionService.
     * createTransaction()), no por created_at -- created_at es cuando
     * se PROCESO la distribucion, sourcePerformance.month/year es a
     * que mes de rendimiento CORRESPONDE, que es lo que la grafica
     * quiere mostrar.
     */
    @Query("SELECT c.sourcePerformance.year, c.sourcePerformance.month, SUM(c.amount) " +
            "FROM CashbackTransaction c WHERE c.position.user = :user AND c.type IN :types " +
            "GROUP BY c.sourcePerformance.year, c.sourcePerformance.month " +
            "ORDER BY c.sourcePerformance.year, c.sourcePerformance.month")
    List<Object[]> sumByPositionUserAndTypeInGroupedByMonth(@Param("user") User user,
                                                              @Param("types") List<CashbackTransactionType> types);

    /** Para CashbackDistributionService.distributeForAnniversaries() */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "LEFT JOIN c.position pos " +
            "WHERE c.user = :user OR pos.user = :user")
    BigDecimal sumAllCashbackForUser(@Param("user") User user);

    /** Idempotencia por posicion: ¿ya recibio esta posicion su pago de este MonthlyPerformance? */
    boolean existsByPositionAndSourcePerformance(InvestmentPosition position, MonthlyPerformance sourcePerformance);

    /**
     * Idempotencia PRIMARIA: solo cuenta MONTHLY_PERFORMANCE (no REASSIGNED).
     * Una posicion puede tener una transaccion REASSIGNED (recibio excedente de
     * otra posicion) y aun asi no haber recibido su propio pago nominal.
     * distribute() usa este metodo -- no el amplio -- para no saltar el pago
     * propio de posiciones que solo tienen transacciones de cascada.
     */
    boolean existsByPositionAndSourcePerformanceAndType(
            InvestmentPosition position,
            MonthlyPerformance sourcePerformance,
            CashbackTransactionType type);

    /** Para CashbackQueryService.getHistory() -- historial completo de todos los tipos de un usuario. */
    @Query("SELECT c FROM CashbackTransaction c " +
            "LEFT JOIN c.position pos " +
            "WHERE c.user = :user OR pos.user = :user " +
            "ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findAllCashbackForUser(@Param("user") User user);

    /** Para AdminCashbackController -- listado de TODAS las transacciones de cashback, sin filtrar por usuario. */
    @Query("SELECT c FROM CashbackTransaction c WHERE c.type IN :types ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findByTypeIn(@Param("types") List<CashbackTransactionType> types);

    /** Para AdminReferralController -- "bonusAmount" del listado de admin: cuanto se le pago efectivamente por esta referencia. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c WHERE c.sourceReferral = :referral")
    BigDecimal sumAmountBySourceReferral(@Param("referral") Referral referral);

    /**
     * Para ReferralController.summary() -- "cuanto he ganado en total
     * por referidos". USA LEFT JOIN explicito en c.position para no
     * excluir REFERRAL_BONUS_DIRECT (que no tiene posicion asociada).
     * Sin LEFT JOIN, el JOIN implicito de JPQL en c.position.user
     * descarta todas las filas donde position IS NULL, que son
     * exactamente los REFERRAL_BONUS_DIRECT del nuevo esquema
     * simplificado (sin restriccion de plan).
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "LEFT JOIN c.position pos " +
            "WHERE c.type IN ('REFERRAL_BONUS', 'REFERRAL_BONUS_DIRECT') " +
            "AND (c.user = :user OR pos.user = :user)")
    BigDecimal sumReferralBonusForUser(@Param("user") User user);

    /** Mismo fix LEFT JOIN -- para el historial de bonos. */
    @Query("SELECT c FROM CashbackTransaction c " +
            "LEFT JOIN c.position pos " +
            "WHERE c.type IN ('REFERRAL_BONUS', 'REFERRAL_BONUS_DIRECT') " +
            "AND (c.user = :user OR pos.user = :user) ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findReferralBonusesForUser(@Param("user") User user);
}
