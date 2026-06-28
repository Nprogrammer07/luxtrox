package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {
    List<WithdrawalRequest> findByUser(User user);
    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);
    long countByStatus(WithdrawalStatus status);

    /** Para AdminReportsService.getStats() -- "pendingWithdrawalsAmount". */
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WithdrawalRequest w WHERE w.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") WithdrawalStatus status);
}