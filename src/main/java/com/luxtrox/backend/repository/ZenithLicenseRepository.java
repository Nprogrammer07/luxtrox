package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.ZenithLicense;
import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ZenithLicenseRepository extends JpaRepository<ZenithLicense, UUID> {
    List<ZenithLicense> findByUser(User user);
    List<ZenithLicense> findByStatus(ZenithLicenseStatus status);
    boolean existsByUser(User user);
}
