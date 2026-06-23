-- V8: Withdrawal Requests (tabla base). El monto se descuenta de
-- available_balance INMEDIATAMENTE al solicitar; si se rechaza, se
-- devuelve. El usuario no puede cancelar -- solo el admin puede
-- rechazar (ver docs/domain-model.md 3.4 y algoritmo 4.3).
CREATE TABLE withdrawal_requests (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id),
    type                   VARCHAR(10) NOT NULL CHECK (type IN ('CRYPTO', 'BANK')),
    amount                 DECIMAL(14,2) NOT NULL CHECK (amount >= 50),
    status                 VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED', 'PAID', 'REJECTED')),
    requested_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at           TIMESTAMPTZ,
    paid_at                TIMESTAMPTZ,
    processed_by_admin_id  UUID REFERENCES users(id),
    admin_notes            VARCHAR(500)
);

CREATE INDEX idx_withdrawals_user ON withdrawal_requests(user_id);
CREATE INDEX idx_withdrawals_status ON withdrawal_requests(status);
