package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
