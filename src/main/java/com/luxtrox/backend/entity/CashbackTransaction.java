package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bitacora INMUTABLE de cada movimiento de cashback hacia una
 * posicion -- nunca se actualiza ni se borra una fila ya creada, solo
 * se insertan nuevas (ver docs/domain-model.md 2.6). Por eso esta
 * clase no expone setters salvo los que el constructor necesita.
 */
@Entity
@Table(name = "cashback_transactions")
public class CashbackTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private InvestmentPosition position;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private CashbackTransactionType type;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_rate", precision = 5, scale = 2)
    private BigDecimal effectiveRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_performance_id")
    private MonthlyPerformance sourcePerformance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_referral_id")
    private Referral sourceReferral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reassigned_from_position_id")
    private InvestmentPosition reassignedFromPosition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CashbackTransaction() {
        // JPA
    }

    public CashbackTransaction(InvestmentPosition position, CashbackTransactionType type, BigDecimal amount) {
        this.position = position;
        this.type = type;
        this.amount = amount;
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

    public InvestmentPosition getPosition() {
        return position;
    }

    public CashbackTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getEffectiveRate() {
        return effectiveRate;
    }

    public void setEffectiveRate(BigDecimal effectiveRate) {
        this.effectiveRate = effectiveRate;
    }

    public MonthlyPerformance getSourcePerformance() {
        return sourcePerformance;
    }

    public void setSourcePerformance(MonthlyPerformance sourcePerformance) {
        this.sourcePerformance = sourcePerformance;
    }

    public Referral getSourceReferral() {
        return sourceReferral;
    }

    public void setSourceReferral(Referral sourceReferral) {
        this.sourceReferral = sourceReferral;
    }

    public InvestmentPosition getReassignedFromPosition() {
        return reassignedFromPosition;
    }

    public void setReassignedFromPosition(InvestmentPosition reassignedFromPosition) {
        this.reassignedFromPosition = reassignedFromPosition;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
