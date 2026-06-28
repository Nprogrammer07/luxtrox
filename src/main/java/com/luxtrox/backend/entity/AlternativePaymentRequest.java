package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.AlternativePaymentStatus;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Flujo manual de pago (no-cripto) con expiracion a las 72h. CONFIRMED
 * dispara: crear posicion, generar factura, enviar correo, evaluar
 * referidos (ver docs/domain-model.md 2.12 y 3.5 -- esa orquestacion
 * vive en el servicio de Fase 6, esta clase es solo el dato).
 */
@Entity
@Table(name = "alternative_payment_requests")
public class AlternativePaymentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    private Purchase purchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AlternativePaymentStatus status = AlternativePaymentStatus.REQUESTED;

    @Column(name = "payment_proof_storage_key")
    private String paymentProofStorageKey;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    protected AlternativePaymentRequest() {
        // JPA
    }

    public AlternativePaymentRequest(Purchase purchase, OffsetDateTime expiresAt) {
        this.purchase = purchase;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public AlternativePaymentStatus getStatus() {
        return status;
    }

    public void setStatus(AlternativePaymentStatus status) {
        this.status = status;
    }

    public String getPaymentProofStorageKey() {
        return paymentProofStorageKey;
    }

    public void setPaymentProofStorageKey(String paymentProofStorageKey) {
        this.paymentProofStorageKey = paymentProofStorageKey;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public User getReviewedByAdmin() {
        return reviewedByAdmin;
    }

    public void setReviewedByAdmin(User reviewedByAdmin) {
        this.reviewedByAdmin = reviewedByAdmin;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}
