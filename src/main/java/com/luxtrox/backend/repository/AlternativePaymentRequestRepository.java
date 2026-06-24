package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.AlternativePaymentRequest;
import com.luxtrox.backend.entity.Purchase;
import com.luxtrox.backend.entity.enums.AlternativePaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlternativePaymentRequestRepository extends JpaRepository<AlternativePaymentRequest, UUID> {
    Optional<AlternativePaymentRequest> findByPurchase(Purchase purchase);
    List<AlternativePaymentRequest> findByStatus(AlternativePaymentStatus status);
}
