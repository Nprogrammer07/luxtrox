package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
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

    /** Para AdminCashbackController -- listado de TODAS las transacciones de cashback, sin filtrar por usuario. */
    @Query("SELECT c FROM CashbackTransaction c WHERE c.type IN :types ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findByTypeIn(@Param("types") List<CashbackTransactionType> types);

    /** Para AdminReferralController -- "bonusAmount" del listado de admin: cuanto se le pago efectivamente por esta referencia. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c WHERE c.sourceReferral = :referral")
    BigDecimal sumAmountBySourceReferral(@Param("referral") Referral referral);

    /**
     * Para ReferralController.summary()/.bonuses() -- "cuanto he
     * ganado en total por referidos". Cubre AMBOS casos de como un
     * bono de referido puede quedar registrado: REFERRAL_BONUS_DIRECT
     * tiene c.user propio (Zenith, pago directo a balance);
     * REFERRAL_BONUS NO tiene c.user (Driver, se aplica como avance a
     * una posicion -- su usuario solo se conoce via c.position.user).
     * El OR cubre los dos sin necesitar dos consultas separadas.
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "WHERE c.type IN ('REFERRAL_BONUS', 'REFERRAL_BONUS_DIRECT') " +
            "AND (c.user = :user OR c.position.user = :user)")
    BigDecimal sumReferralBonusForUser(@Param("user") User user);

    @Query("SELECT c FROM CashbackTransaction c " +
            "WHERE c.type IN ('REFERRAL_BONUS', 'REFERRAL_BONUS_DIRECT') " +
            "AND (c.user = :user OR c.position.user = :user) ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findReferralBonusesForUser(@Param("user") User user);
}
