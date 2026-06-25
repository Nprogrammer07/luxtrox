package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CashbackTransaction;
import com.luxtrox.backend.entity.InvestmentPosition;
import com.luxtrox.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, UUID> {
    List<CashbackTransaction> findByPosition(InvestmentPosition position);

    /** Solo encuentra las de tipo REFERRAL_BONUS_DIRECT (las unicas con user_id propio). */
    List<CashbackTransaction> findByUser(User user);
}
