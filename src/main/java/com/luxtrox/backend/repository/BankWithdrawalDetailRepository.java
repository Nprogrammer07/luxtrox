package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.BankWithdrawalDetail;
import com.luxtrox.backend.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BankWithdrawalDetailRepository extends JpaRepository<BankWithdrawalDetail, UUID> {
    Optional<BankWithdrawalDetail> findByWithdrawalRequest(WithdrawalRequest withdrawalRequest);
}
