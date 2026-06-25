package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.ZenithLicenseStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una licencia por compra Zenith confirmada (ver docs/domain-model.md
 * 7.1, adenda de Fase 6). A diferencia de InvestmentPosition, NO
 * participa del motor de cashback -- no tiene capital, no tiene
 * target_cashback, no recibe rendimiento mensual. Solo necesita
 * renovarse anualmente ($250 fijos, ver ZenithRenewalPayment).
 */
@Entity
@Table(name = "zenith_licenses")
public class ZenithLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    private Purchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ZenithLicenseStatus status = ZenithLicenseStatus.ACTIVE;

    @Column(name = "activated_at", nullable = false)
    private OffsetDateTime activatedAt;

    @Column(name = "current_period_end", nullable = false)
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ZenithLicense() {
        // JPA
    }

    public ZenithLicense(User user, Purchase purchase, OffsetDateTime activatedAt, OffsetDateTime currentPeriodEnd) {
        this.user = user;
        this.purchase = purchase;
        this.activatedAt = activatedAt;
        this.currentPeriodEnd = currentPeriodEnd;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public ZenithLicenseStatus getStatus() {
        return status;
    }

    public void setStatus(ZenithLicenseStatus status) {
        this.status = status;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
