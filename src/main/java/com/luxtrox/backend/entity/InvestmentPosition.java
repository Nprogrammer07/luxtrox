package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.PositionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * capital y target_cashback son inmutables desde la creacion
 * (target_cashback = capital * 3.0, 300%). Ver docs/domain-model.md
 * 2.4 y seccion 4.1 para el algoritmo que mueve cashback_paid /
 * cashback_remaining (eso vive en el servicio de Fase 6, no aqui).
 */
@Entity
@Table(name = "investment_positions")
public class InvestmentPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Bloqueo optimista -- el reparto de rendimiento mensual y una
     * comision de referido pueden caer casi al mismo tiempo sobre la
     * misma posicion (ver User.version para el detalle completo del
     * problema que esto evita).
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    private Purchase purchase;

    @Column(name = "capital", nullable = false, precision = 14, scale = 2)
    private BigDecimal capital;

    @Column(name = "target_cashback", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetCashback;

    @Column(name = "cashback_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashbackPaid = BigDecimal.ZERO;

    @Column(name = "cashback_remaining", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashbackRemaining;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PositionStatus status = PositionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected InvestmentPosition() {
        // JPA
    }

    public InvestmentPosition(User user, Purchase purchase, BigDecimal capital, BigDecimal targetCashback) {
        this.user = user;
        this.purchase = purchase;
        this.capital = capital;
        this.targetCashback = targetCashback;
        this.cashbackRemaining = targetCashback;
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

    public BigDecimal getCapital() {
        return capital;
    }

    public BigDecimal getTargetCashback() {
        return targetCashback;
    }

    public BigDecimal getCashbackPaid() {
        return cashbackPaid;
    }

    public void setCashbackPaid(BigDecimal cashbackPaid) {
        this.cashbackPaid = cashbackPaid;
    }

    public BigDecimal getCashbackRemaining() {
        return cashbackRemaining;
    }

    public void setCashbackRemaining(BigDecimal cashbackRemaining) {
        this.cashbackRemaining = cashbackRemaining;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public void setStatus(PositionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
