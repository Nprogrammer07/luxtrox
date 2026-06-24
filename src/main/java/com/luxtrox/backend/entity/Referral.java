package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.ReferralStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un registro por usuario referido (unique referred_user_id). El bono
 * de $100 solo se paga cuando referente Y referido cumplen sus
 * condiciones -- puede quedar QUALIFIED_AWAITING_REFERRER si el
 * referente todavia no tiene ninguna posicion propia (ver
 * docs/domain-model.md 3.3 y 4.2). La logica de transicion vive en el
 * servicio de Fase 6, esta clase es solo el dato.
 */
@Entity
@Table(name = "referrals")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private User referrer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_user_id", nullable = false, unique = true)
    private User referred;

    @Column(name = "referral_code_used", nullable = false, length = 20)
    private String referralCodeUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReferralStatus status = ReferralStatus.PENDING_PURCHASE;

    @Column(name = "qualified_at")
    private OffsetDateTime qualifiedAt;

    @Column(name = "bonus_paid_at")
    private OffsetDateTime bonusPaidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_position_id")
    private InvestmentPosition targetPosition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Referral() {
        // JPA
    }

    public Referral(User referrer, User referred, String referralCodeUsed) {
        this.referrer = referrer;
        this.referred = referred;
        this.referralCodeUsed = referralCodeUsed;
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

    public User getReferrer() {
        return referrer;
    }

    public User getReferred() {
        return referred;
    }

    public String getReferralCodeUsed() {
        return referralCodeUsed;
    }

    public ReferralStatus getStatus() {
        return status;
    }

    public void setStatus(ReferralStatus status) {
        this.status = status;
    }

    public OffsetDateTime getQualifiedAt() {
        return qualifiedAt;
    }

    public void setQualifiedAt(OffsetDateTime qualifiedAt) {
        this.qualifiedAt = qualifiedAt;
    }

    public OffsetDateTime getBonusPaidAt() {
        return bonusPaidAt;
    }

    public void setBonusPaidAt(OffsetDateTime bonusPaidAt) {
        this.bonusPaidAt = bonusPaidAt;
    }

    public InvestmentPosition getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(InvestmentPosition targetPosition) {
        this.targetPosition = targetPosition;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
