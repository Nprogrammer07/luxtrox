package com.luxtrox.backend.repository;

import com.luxtrox.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByEmail(String email);
}
