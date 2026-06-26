package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una factura por compra confirmada. El PDF se guarda en Supabase
 * Storage (S3-compatible) -- aqui solo se guarda la referencia/key, no
 * el archivo. Se envia por correo via Resend (ver docs/domain-model.md
 * 2.11 y adenda de Fase 7).
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, unique = true)
    private Purchase purchase;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    @Column(name = "pdf_storage_key", nullable = false)
    private String pdfStorageKey;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected Invoice() {
        // JPA
    }

    public Invoice(Purchase purchase, String invoiceNumber, String pdfStorageKey) {
        this.purchase = purchase;
        this.invoiceNumber = invoiceNumber;
        this.pdfStorageKey = pdfStorageKey;
    }

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getPdfStorageKey() {
        return pdfStorageKey;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
