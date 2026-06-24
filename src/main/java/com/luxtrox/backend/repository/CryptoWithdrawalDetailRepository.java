package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.CryptoWithdrawalDetail;
import com.luxtrox.backend.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CryptoWithdrawalDetailRepository extends JpaRepository<CryptoWithdrawalDetail, UUID> {
    Optional<CryptoWithdrawalDetail> findByWithdrawalRequest(WithdrawalRequest withdrawalRequest);
}
