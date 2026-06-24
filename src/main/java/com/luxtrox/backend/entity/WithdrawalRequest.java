package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.WithdrawalStatus;
import com.luxtrox.backend.entity.enums.WithdrawalType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El monto se descuenta de available_balance INMEDIATAMENTE al
 * solicitar; si se rechaza, se devuelve. El usuario no puede
 * cancelar -- solo el admin puede rechazar (ver docs/domain-model.md
 * 3.4 y algoritmo 4.3, logica en el servicio de Fase 6).
 */
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private WithdrawalType type;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawalStatus status = WithdrawalStatus.REQUESTED;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_admin_id")
    private User processedByAdmin;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    protected WithdrawalRequest() {
        // JPA
    }

    public WithdrawalRequest(User user, WithdrawalType type, BigDecimal amount) {
        this.user = user;
        this.type = type;
        this.amount = amount;
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public WithdrawalType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public void setStatus(WithdrawalStatus status) {
        this.status = status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public User getProcessedByAdmin() {
        return processedByAdmin;
    }

    public void setProcessedByAdmin(User processedByAdmin) {
        this.processedByAdmin = processedByAdmin;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}
