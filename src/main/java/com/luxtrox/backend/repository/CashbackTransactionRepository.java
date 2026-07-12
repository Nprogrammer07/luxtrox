package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, UUID> {

    List<CashbackTransaction> findByUser(User user);

    List<CashbackTransaction> findByUserOrderByCreatedAtDesc(User user);

    /** Total de cashback recibido por un usuario (comisiones + créditos manuales). */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c WHERE c.user = :user")
    BigDecimal sumAllForUser(@Param("user") User user);

    /** Total global de todo el dinero repartido (para stats de admin). */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c")
    BigDecimal sumAllCommissionsAndCredits();

    /** Suma de comisiones de referido de un usuario. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "WHERE c.type = 'REFERRAL_BONUS_DIRECT' AND c.user = :user")
    BigDecimal sumReferralBonusForUser(@Param("user") User user);

    /** Historial de comisiones de referido (pestaña Bonificaciones). */
    @Query("SELECT c FROM CashbackTransaction c " +
            "WHERE c.type = 'REFERRAL_BONUS_DIRECT' AND c.user = :user " +
            "ORDER BY c.createdAt DESC")
    List<CashbackTransaction> findReferralBonusesForUser(@Param("user") User user);

    /** Suma de comisiones generadas por un referral específico. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CashbackTransaction c " +
            "WHERE c.sourceReferral = :referral")
    BigDecimal sumAmountBySourceReferral(@Param("referral") Referral referral);
}
