package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bitacora INMUTABLE de cada movimiento de cashback -- nunca se
 * actualiza ni se borra una fila ya creada, solo se insertan nuevas
 * (ver docs/domain-model.md 2.6). Por eso esta clase no expone
 * setters salvo los que el constructor necesita.
 *
 * Dos formas mutuamente excluyentes de identificar al destinatario
 * (ver docs/domain-model.md 7.3, adenda de Fase 6):
 *  - type != REFERRAL_BONUS_DIRECT  -> SIEMPRE position (se deriva
 *    el usuario via position.getUser()), NUNCA user directo.
 *  - type == REFERRAL_BONUS_DIRECT  -> SIEMPRE user directo (no hay
 *    posicion de donde derivarlo -- la comisión fue directo al
 *    available_balance sin pasar por ninguna posicion), NUNCA position.
 * Esto esta forzado por el CHECK chk_referral_direct_no_position en
 * la base de datos, no solo por convencion en este codigo.
 */
@Entity
@Table(name = "cashback_transactions")
public class CashbackTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private InvestmentPosition position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

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

    /** Para todo tipo que SI esta asociado a una posicion (todos salvo REFERRAL_BONUS_DIRECT). */
    public CashbackTransaction(InvestmentPosition position, CashbackTransactionType type, BigDecimal amount) {
        if (type == CashbackTransactionType.REFERRAL_BONUS_DIRECT) {
            throw new IllegalArgumentException(
                    "REFERRAL_BONUS_DIRECT no lleva position -- usar el constructor con User");
        }
        this.position = position;
        this.type = type;
        this.amount = amount;
    }

    /** Exclusivo para REFERRAL_BONUS_DIRECT -- paga directo a available_balance, sin posicion. */
    public CashbackTransaction(User user, BigDecimal amount) {
        this.user = user;
        this.type = CashbackTransactionType.REFERRAL_BONUS_DIRECT;
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

    public User getUser() {
        return user;
    }

    /** El usuario destinatario, sea por posicion o directo (conveniencia para el servicio). */
    public User resolveRecipient() {
        return position != null ? position.getUser() : user;
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
