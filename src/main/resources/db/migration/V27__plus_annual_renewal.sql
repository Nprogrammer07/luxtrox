-- V27: Luxtrox Genius (interno: PLUS) pasa de licencia única de 5 años
-- a matrícula ANUAL de $200 renovable.
--
-- Modelo espejo del de Zenith: la licencia tiene un current_period_end;
-- al renovar se extiende un año más y se registra el pago.

-- 1. Renombrar expires_at → current_period_end (semántica de periodo, no de fin definitivo)
ALTER TABLE plus_licenses RENAME COLUMN expires_at TO current_period_end;

-- 2. Ajustar las licencias existentes: de 5 años a 1 año desde la compra
UPDATE plus_licenses
SET current_period_end = purchased_at + INTERVAL '1 year';

-- 3. Tabla de pagos de renovación (espejo de zenith_renewal_payments)
CREATE TABLE plus_renewal_payments (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    license_id   UUID        NOT NULL REFERENCES plus_licenses(id) ON DELETE CASCADE,
    amount       NUMERIC(14,2) NOT NULL DEFAULT 200.00
                 CHECK (amount = 200.00),
    period_start TIMESTAMPTZ NOT NULL,
    period_end   TIMESTAMPTZ NOT NULL,
    paid_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plus_renewal_payments_license_id ON plus_renewal_payments(license_id);