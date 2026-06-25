package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pago de renovacion anual de una ZenithLicense -- siempre $250 fijos
 * (forzado tambien por CHECK en la base de datos, no solo aqui). Ver
 * docs/domain-model.md 7.1.
 */
@Entity
@Table(name = "zenith_renewal_payments")
public class ZenithRenewalPayment {

    public static final BigDecimal RENEWAL_AMOUNT = new BigDecimal("250.00");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false)
    private ZenithLicense license;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = RENEWAL_AMOUNT;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "paid_at", nullable = false)
    private OffsetDateTime paidAt;

    protected ZenithRenewalPayment() {
        // JPA
    }

    public ZenithRenewalPayment(ZenithLicense license, OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        this.license = license;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    @PrePersist
    void onCreate() {
        if (paidAt == null) {
            paidAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public ZenithLicense getLicense() {
        return license;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OffsetDateTime getPeriodStart() {
        return periodStart;
    }

    public OffsetDateTime getPeriodEnd() {
        return periodEnd;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }
}
