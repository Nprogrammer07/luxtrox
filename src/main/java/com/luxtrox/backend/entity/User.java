package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.UserStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Bloqueo optimista -- protege available_balance contra
     * actualizaciones concurrentes perdidas (dos retiros simultaneos
     * del mismo usuario, por ejemplo). JPA la incrementa solo; si dos
     * transacciones leen la misma version y ambas intentan guardar,
     * la segunda en llegar falla con OptimisticLockingFailureException
     * en vez de pisar silenciosamente a la primera.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "referral_code", nullable = false, unique = true, length = 20)
    private String referralCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_user_id")
    private User referredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "total_packages_purchased", nullable = false)
    private Integer totalPackagesPurchased = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {
        // JPA
    }

    public User(String fullName, String email, String phone, String passwordHash,
                Role role, String referralCode) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.referralCode = referralCode;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public User getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(User referredBy) {
        this.referredBy = referredBy;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public Integer getTotalPackagesPurchased() {
        return totalPackagesPurchased;
    }

    public void setTotalPackagesPurchased(Integer totalPackagesPurchased) {
        this.totalPackagesPurchased = totalPackagesPurchased;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
