package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.WithdrawalRequest;
import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {
    List<WithdrawalRequest> findByUser(User user);
    List<WithdrawalRequest> findByStatus(WithdrawalStatus status);
}
