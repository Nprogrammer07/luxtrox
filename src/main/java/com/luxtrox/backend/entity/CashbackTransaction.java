package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.CashbackTransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de dinero acreditado al usuario.
 * Tras eliminar el módulo Driver, solo existen dos tipos:
 *   REFERRAL_BONUS_DIRECT — comisión de referido (Zenith 22%, Plus 25%)
 *   MANUAL_CREDIT         — crédito manual del admin
 */
@Entity
@Table(name = "cashback_transactions")
public class CashbackTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CashbackTransactionType type;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_referral_id")
    private Referral sourceReferral;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = OffsetDateTime.now(); }

    protected CashbackTransaction() {}

    public CashbackTransaction(User user, CashbackTransactionType type, BigDecimal amount) {
        this.user = user;
        this.type = type;
        this.amount = amount;
    }

    public UUID getId()                      { return id; }
    public User getUser()                    { return user; }
    public CashbackTransactionType getType() { return type; }
    public BigDecimal getAmount()            { return amount; }
    public Referral getSourceReferral()      { return sourceReferral; }
    public String getNotes()                 { return notes; }
    public OffsetDateTime getCreatedAt()     { return createdAt; }
    public void setSourceReferral(Referral r){ this.sourceReferral = r; }
    public void setNotes(String notes)       { this.notes = notes; }
}
