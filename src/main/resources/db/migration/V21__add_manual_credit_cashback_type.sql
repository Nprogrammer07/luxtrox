-- V21: Actualiza el CHECK constraint de cashback_transactions.type para
-- permitir los nuevos valores REFERRAL_BONUS_DIRECT y MANUAL_CREDIT.
-- El tipo es VARCHAR(40), no un enum nativo de Postgres (ver V7) --
-- cualquier valor nuevo del enum Java solo requiere actualizar este
-- constraint, no un ALTER TYPE.
ALTER TABLE cashback_transactions DROP CONSTRAINT IF EXISTS cashback_transactions_type_check;

-- Agrega columna user_id (para REFERRAL_BONUS_DIRECT y MANUAL_CREDIT,
-- que van directo a un usuario sin posicion asociada -- ver CashbackTransaction).
ALTER TABLE cashback_transactions ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id);

ALTER TABLE cashback_transactions ADD CONSTRAINT cashback_transactions_type_check
    CHECK (type IN (
        'MONTHLY_PERFORMANCE',
        'MONTHLY_PERFORMANCE_REASSIGNED',
        'REFERRAL_BONUS',
        'REFERRAL_BONUS_DIRECT',
        'MANUAL_CREDIT'
    ));