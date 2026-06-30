-- V22: Agrega columna notes a cashback_transactions.
-- Solo se llena para MANUAL_CREDIT -- para las demas transacciones
-- queda NULL. VARCHAR(500) es suficiente para un motivo breve.
ALTER TABLE cashback_transactions ADD COLUMN IF NOT EXISTS notes VARCHAR(500);