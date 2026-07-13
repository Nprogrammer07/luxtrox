-- V26: Eliminación completa del módulo Luxtrox Driver.
--
-- IMPORTANTE: borra las compras y posiciones Driver existentes. Se
-- verificó antes de aplicar que no había cashback distribuido ni saldo
-- de usuario asociado a ellas (available_balance vive en users y NO se
-- toca aquí -- ese saldo se conserva intacto).

-- 1. Quitar columnas FK que referencian tablas Driver
ALTER TABLE cashback_transactions DROP COLUMN IF EXISTS position_id;
ALTER TABLE cashback_transactions DROP COLUMN IF EXISTS source_performance_id;
ALTER TABLE purchases              DROP COLUMN IF EXISTS position_id;
ALTER TABLE referrals              DROP COLUMN IF EXISTS target_position_id;

-- 2. Limpiar cashback_transactions de tipos que ya no existen
ALTER TABLE cashback_transactions DROP CONSTRAINT IF EXISTS cashback_transactions_type_check;
DELETE FROM cashback_transactions
WHERE type NOT IN ('REFERRAL_BONUS_DIRECT', 'MANUAL_CREDIT');
ALTER TABLE cashback_transactions ADD CONSTRAINT cashback_transactions_type_check
    CHECK (type IN ('REFERRAL_BONUS_DIRECT', 'MANUAL_CREDIT'));

-- 3. Limpiar referrals que apuntan a compras Driver (antes de borrarlas)
UPDATE referrals
SET triggering_purchase_id = NULL
WHERE triggering_purchase_id IN (
    SELECT id FROM purchases WHERE plan_type = 'DRIVER'
);

-- 4. Borrar facturas y solicitudes de pago de compras Driver
DELETE FROM invoices
WHERE purchase_id IN (SELECT id FROM purchases WHERE plan_type = 'DRIVER');

DELETE FROM alternative_payment_requests
WHERE purchase_id IN (SELECT id FROM purchases WHERE plan_type = 'DRIVER');

-- 5. Borrar las compras Driver (deben irse ANTES del nuevo CHECK)
DELETE FROM purchases WHERE plan_type = 'DRIVER';

-- 6. Ahora sí, aplicar el nuevo constraint de plan_type
ALTER TABLE purchases DROP CONSTRAINT IF EXISTS purchases_plan_type_check;
ALTER TABLE purchases ADD CONSTRAINT purchases_plan_type_check
    CHECK (plan_type IN ('ZENITH', 'PLUS'));

-- 7. Drop tablas Driver
DROP TABLE IF EXISTS monthly_performances CASCADE;
DROP TABLE IF EXISTS investment_positions CASCADE;

-- 8. Resetear el contador de paquetes de los usuarios (era exclusivo de Driver)
UPDATE users SET total_packages_purchased = 0;

-- 9. Limpiar system_config de claves exclusivas de Driver
DELETE FROM system_config
WHERE key IN ('driver_price', 'max_driver_positions', 'cashback_rate_pct');