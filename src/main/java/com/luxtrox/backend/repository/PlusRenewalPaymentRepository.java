package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.PlusLicense;
import com.luxtrox.backend.entity.PlusRenewalPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlusRenewalPaymentRepository extends JpaRepository<PlusRenewalPayment, UUID> {

    List<PlusRenewalPayment> findByLicense(PlusLicense license);
}
