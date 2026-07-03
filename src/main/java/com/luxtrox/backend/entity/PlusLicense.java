package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.PlusLicenseStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Licencia Luxtrox Plus -- paquete educativo de 5 años.
 * Una fila por compra confirmada. Sin renovaciones (una nueva compra
 * crea una nueva licencia). El precio es $200 con comision de referido
 * de $50 (25%) y otorga $100 de descuento al comprar Zenith.
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

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PlusLicense() {}

    public PlusLicense(User user, Purchase purchase) {
        this.user = user;
        this.purchase = purchase;
        this.purchasedAt = OffsetDateTime.now();
        this.expiresAt = this.purchasedAt.plusYears(5);
        this.status = PlusLicenseStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void touch() { this.updatedAt = OffsetDateTime.now(); }

    public UUID getId()                      { return id; }
    public User getUser()                    { return user; }
    public Purchase getPurchase()            { return purchase; }
    public PlusLicenseStatus getStatus()     { return status; }
    public OffsetDateTime getPurchasedAt()   { return purchasedAt; }
    public OffsetDateTime getExpiresAt()     { return expiresAt; }
    public OffsetDateTime getCreatedAt()     { return createdAt; }
    public boolean isActive()                { return status == PlusLicenseStatus.ACTIVE; }
    public void setStatus(PlusLicenseStatus status) { this.status = status; }
}
