-- V26: Eliminación completa del módulo Luxtrox Driver.

-- 1. Quitar columnas FK que referencian tablas Driver
ALTER TABLE cashback_transactions DROP COLUMN IF EXISTS position_id;
ALTER TABLE cashback_transactions DROP COLUMN IF EXISTS source_performance_id;
ALTER TABLE purchases              DROP COLUMN IF EXISTS position_id;
ALTER TABLE referrals              DROP COLUMN IF EXISTS target_position_id;

-- 2. CHECK constraint de cashback_transactions.type
ALTER TABLE cashback_transactions DROP CONSTRAINT IF EXISTS cashback_transactions_type_check;
DELETE FROM cashback_transactions
WHERE type NOT IN ('REFERRAL_BONUS_DIRECT', 'MANUAL_CREDIT');
ALTER TABLE cashback_transactions ADD CONSTRAINT cashback_transactions_type_check
    CHECK (type IN ('REFERRAL_BONUS_DIRECT', 'MANUAL_CREDIT'));

-- 3. CHECK constraint de purchases.plan_type
ALTER TABLE purchases DROP CONSTRAINT IF EXISTS purchases_plan_type_check;
ALTER TABLE purchases ADD CONSTRAINT purchases_plan_type_check
    CHECK (plan_type IN ('ZENITH', 'PLUS'));

-- 4. Drop tablas Driver
DROP TABLE IF EXISTS monthly_performances CASCADE;
DROP TABLE IF EXISTS investment_positions CASCADE;

-- 5. Limpiar system_config
DELETE FROM system_config
WHERE key IN ('driver_price', 'max_driver_positions', 'cashback_rate_pct');