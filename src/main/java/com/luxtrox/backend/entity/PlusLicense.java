package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.PlusLicenseStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Licencia Luxtrox Genius (interno: PLUS) -- matrícula ANUAL de $200.
 *
 * Modelo espejo del de Zenith: currentPeriodEnd marca hasta cuándo tiene
 * acceso el alumno a los recursos académicos. Al renovar se extiende un
 * año más (ver PlusService.renew). Si no renueva a tiempo, la licencia
 * pasa a EXPIRED y pierde el acceso (y el descuento en Zenith).
 */
@Entity
@Table(name = "plus_licenses")
public class PlusLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlusLicenseStatus status = PlusLicenseStatus.ACTIVE;

    @Column(name = "purchased_at", nullable = false)
    private OffsetDateTime purchasedAt;

    /** Hasta cuándo tiene acceso. Se extiende 1 año en cada renovación. */
    @Column(name = "current_period_end", nullable = false)
    private OffsetDateTime currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlusLicense() {}

    public PlusLicense(User user, Purchase purchase) {
        this.user = user;
        this.purchase = purchase;
        this.purchasedAt = OffsetDateTime.now();
        this.currentPeriodEnd = this.purchasedAt.plusYears(1);
        this.status = PlusLicenseStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId()                          { return id; }
    public User getUser()                        { return user; }
    public Purchase getPurchase()                { return purchase; }
    public PlusLicenseStatus getStatus()         { return status; }
    public OffsetDateTime getPurchasedAt()       { return purchasedAt; }
    public OffsetDateTime getCurrentPeriodEnd()  { return currentPeriodEnd; }
    public OffsetDateTime getCreatedAt()         { return createdAt; }
    public boolean isActive()                    { return status == PlusLicenseStatus.ACTIVE; }

    public void setStatus(PlusLicenseStatus status) { this.status = status; }
    public void setCurrentPeriodEnd(OffsetDateTime end) { this.currentPeriodEnd = end; }
}
