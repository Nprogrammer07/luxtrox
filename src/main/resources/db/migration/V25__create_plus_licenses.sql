-- V25: Módulo Luxtrox Plus
-- Tabla plus_licenses: una fila por compra confirmada, vigencia 5 años.
-- El plan Plus no tiene renovaciones -- al expirar se puede recomprar
-- como una compra nueva. La comision de referido ($50 flat = 25% de $200)
-- usa el tipo REFERRAL_BONUS_DIRECT ya existente en cashback_transactions.

CREATE TABLE plus_licenses (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purchase_id  UUID        REFERENCES purchases(id) ON DELETE SET NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                 CHECK (status IN ('ACTIVE', 'EXPIRED')),
    purchased_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ NOT NULL, -- purchased_at + 5 years
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plus_licenses_user_id ON plus_licenses(user_id);
CREATE INDEX idx_plus_licenses_status  ON plus_licenses(status);

-- Agrega PLUS al constraint de plan_type en purchases.
-- Si la tabla no tiene constraint, el INSERT con 'PLUS' funciona igual.
ALTER TABLE purchases DROP CONSTRAINT IF EXISTS purchases_plan_type_check;
ALTER TABLE purchases ADD CONSTRAINT purchases_plan_type_check
    CHECK (plan_type IN ('DRIVER', 'ZENITH', 'PLUS'));