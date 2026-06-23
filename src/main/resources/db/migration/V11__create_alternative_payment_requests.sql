-- V11: Alternative Payment Requests. Flujo manual de pago (no-cripto)
-- con expiracion a las 72h. CONFIRMED dispara: crear posicion, generar
-- factura, enviar correo, evaluar referidos (ver docs/domain-model.md
-- 2.12 y 3.5).
CREATE TABLE alternative_payment_requests (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id              UUID NOT NULL UNIQUE REFERENCES purchases(id),
    status                   VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN (
            'REQUESTED', 'APPROVED', 'PAYMENT_PROOF_PENDING',
            'UNDER_REVIEW', 'CONFIRMED', 'REJECTED', 'EXPIRED'
        )),
    payment_proof_storage_key  VARCHAR(255),
    expires_at                 TIMESTAMPTZ NOT NULL,
    reviewed_by_admin_id       UUID REFERENCES users(id),
    reviewed_at                TIMESTAMPTZ
);

CREATE INDEX idx_alt_payment_status ON alternative_payment_requests(status);
