package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.ReferralStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un registro por usuario referido. La comisión se evalúa y paga cada
 * vez que el referido confirma una compra (Zenith 22%, Plus 25%).
 * El historial completo vive en cashback_transactions.
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

    /**
     * La compra del REFERIDO que calificó esta referral -- necesaria
     * para saber si la comisión es 22% (Zenith) o 25% (Plus).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggering_purchase_id")
    private Purchase triggeringPurchase;

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

    public UUID getId() { return id; }
    public User getReferrer() { return referrer; }
    public User getReferred() { return referred; }
    public String getReferralCodeUsed() { return referralCodeUsed; }
    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }
    public OffsetDateTime getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(OffsetDateTime qualifiedAt) { this.qualifiedAt = qualifiedAt; }
    public OffsetDateTime getBonusPaidAt() { return bonusPaidAt; }
    public void setBonusPaidAt(OffsetDateTime bonusPaidAt) { this.bonusPaidAt = bonusPaidAt; }
    public Purchase getTriggeringPurchase() { return triggeringPurchase; }
    public void setTriggeringPurchase(Purchase triggeringPurchase) { this.triggeringPurchase = triggeringPurchase; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}