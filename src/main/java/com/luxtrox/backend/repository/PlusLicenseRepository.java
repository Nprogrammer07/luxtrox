package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.PlusLicense;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.PlusLicenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlusLicenseRepository extends JpaRepository<PlusLicense, UUID> {

    List<PlusLicense> findByUserOrderByPurchasedAtDesc(User user);

    boolean existsByUserAndStatus(User user, PlusLicenseStatus status);

    long countByStatus(PlusLicenseStatus status);
}