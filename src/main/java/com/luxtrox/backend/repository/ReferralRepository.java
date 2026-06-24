package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.Referral;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.entity.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {
    Optional<Referral> findByReferred(User referred);
    List<Referral> findByReferrerAndStatus(User referrer, ReferralStatus status);
}
