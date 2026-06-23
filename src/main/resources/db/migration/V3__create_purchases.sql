-- V3: Purchases. Una compra siempre genera UNA sola posicion sin
-- importar cuantos paquetes contenga (ver docs/domain-model.md 2.3).
-- position_id se deja como UUID simple aqui (sin FK todavia) porque
-- investment_positions aun no existe -- la referencia se agrega en V4
-- una vez creada esa tabla (dependencia circular 1:1).
CREATE TABLE purchases (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    package_quantity  INT NOT NULL CHECK (package_quantity BETWEEN 1 AND 30),
    total_amount      DECIMAL(14,2) NOT NULL CHECK (total_amount > 0),
    payment_method    VARCHAR(20) NOT NULL
        CHECK (payment_method IN ('CRYPTO', 'ALTERNATIVE')),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED')),
    position_id       UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at      TIMESTAMPTZ
);

CREATE INDEX idx_purchases_user ON purchases(user_id);
CREATE INDEX idx_purchases_status ON purchases(status);
