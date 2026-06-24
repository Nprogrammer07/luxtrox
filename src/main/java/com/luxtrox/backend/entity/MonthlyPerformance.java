package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un solo rendimiento por mes calendario (UNIQUE month+year en la BD).
 * applied_at NULL = registrado pero todavia no distribuido a las
 * posiciones (ver docs/domain-model.md 2.5 y algoritmo 4.1).
 */
@Entity
@Table(name = "monthly_performances")
public class MonthlyPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by_admin_id", nullable = false)
    private User registeredByAdmin;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected MonthlyPerformance() {
        // JPA
    }

    public MonthlyPerformance(Integer month, Integer year, BigDecimal percentage, User registeredByAdmin) {
        this.month = month;
        this.year = year;
        this.percentage = percentage;
        this.registeredByAdmin = registeredByAdmin;
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

    public Integer getMonth() {
        return month;
    }

    public Integer getYear() {
        return year;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public User getRegisteredByAdmin() {
        return registeredByAdmin;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(OffsetDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
