-- V17: Rastreo del invoice de NOWPayments para compras CRYPTO (ver
-- Fase 7 -- Integraciones externas). Nullable porque las compras
-- ALTERNATIVE nunca pasan por NOWPayments.
ALTER TABLE purchases ADD COLUMN nowpayments_invoice_id VARCHAR(100);

CREATE INDEX idx_purchases_nowpayments_invoice ON purchases(nowpayments_invoice_id);
