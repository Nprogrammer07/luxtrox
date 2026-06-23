-- V4: Investment Positions. capital y target_cashback son inmutables
-- desde la creacion (target_cashback = capital * 3.0, 300%). Ver
-- docs/domain-model.md 2.4 y seccion 4.1 para el algoritmo que mueve
-- cashback_paid / cashback_remaining.
CREATE TABLE investment_positions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    purchase_id         UUID NOT NULL UNIQUE REFERENCES purchases(id),
    capital             DECIMAL(14,2) NOT NULL CHECK (capital > 0),
    target_cashback     DECIMAL(14,2) NOT NULL CHECK (target_cashback > 0),
    cashback_paid       DECIMAL(14,2) NOT NULL DEFAULT 0 CHECK (cashback_paid >= 0),
    cashback_remaining  DECIMAL(14,2) NOT NULL CHECK (cashback_remaining >= 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    CONSTRAINT chk_position_balances
        CHECK (cashback_paid + cashback_remaining = target_cashback)
);

CREATE INDEX idx_positions_user ON investment_positions(user_id);
CREATE INDEX idx_positions_status ON investment_positions(status);
-- La cascada de reasignacion de excedentes busca "la posicion ACTIVA
-- mas reciente del usuario con saldo pendiente" -- este indice
-- compuesto es exactamente ese acceso (ver docs/domain-model.md 4.1).
CREATE INDEX idx_positions_user_status_created
    ON investment_positions(user_id, status, created_at DESC);

-- Cierra la dependencia circular 1:1 entre purchases <-> investment_positions
ALTER TABLE purchases
    ADD CONSTRAINT fk_purchases_position
    FOREIGN KEY (position_id) REFERENCES investment_positions(id);
