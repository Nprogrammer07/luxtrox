-- V10: Invoices. Una factura por compra confirmada, PDF almacenado en
-- Cloudflare R2 (solo se guarda la referencia/key, no el archivo) y
-- enviada por correo via Resend (ver docs/domain-model.md 2.11).
CREATE TABLE invoices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id       UUID NOT NULL UNIQUE REFERENCES purchases(id),
    invoice_number    VARCHAR(30) NOT NULL UNIQUE,
    pdf_storage_key   VARCHAR(255) NOT NULL,
    issued_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at           TIMESTAMPTZ
);
