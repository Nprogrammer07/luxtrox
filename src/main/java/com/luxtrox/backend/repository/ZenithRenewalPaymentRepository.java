package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.ZenithLicense;
import com.luxtrox.backend.entity.ZenithRenewalPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ZenithRenewalPaymentRepository extends JpaRepository<ZenithRenewalPayment, UUID> {
    List<ZenithRenewalPayment> findByLicense(ZenithLicense license);
}
