-- V26: Eliminación completa del módulo Luxtrox Driver.
--
-- Orden crítico: primero se borran las TABLAS de Driver (que tienen FKs
-- hacia purchases), luego las filas de purchases, y solo entonces se
-- aplica el nuevo CHECK constraint.
--
-- available_balance de los usuarios vive en la tabla `users` y NO se
-- toca aquí -- el saldo retirable se conserva intacto.

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

-- 3. DROP de las tablas Driver PRIMERO -- tienen FK purchase_id hacia
--    purchases, así que deben irse antes de poder borrar esas compras.
DROP TABLE IF EXISTS monthly_performances CASCADE;
DROP TABLE IF EXISTS investment_positions CASCADE;

-- 4. Limpiar referencias a compras Driver en otras tablas
UPDATE referrals
SET triggering_purchase_id = NULL
WHERE triggering_purchase_id IN (
    SELECT id FROM purchases WHERE plan_type = 'DRIVER'
);

DELETE FROM invoices
WHERE purchase_id IN (SELECT id FROM purchases WHERE plan_type = 'DRIVER');

DELETE FROM alternative_payment_requests
WHERE purchase_id IN (SELECT id FROM purchases WHERE plan_type = 'DRIVER');

-- 5. Ahora sí se pueden borrar las compras Driver
DELETE FROM purchases WHERE plan_type = 'DRIVER';

-- 6. Y recién ahora aplicar el nuevo CHECK constraint
ALTER TABLE purchases DROP CONSTRAINT IF EXISTS purchases_plan_type_check;
ALTER TABLE purchases ADD CONSTRAINT purchases_plan_type_check
    CHECK (plan_type IN ('ZENITH', 'PLUS'));

-- 7. Resetear el contador de paquetes (era exclusivo de Driver)
UPDATE users SET total_packages_purchased = 0;

-- 8. Limpiar system_config de claves exclusivas de Driver
DELETE FROM system_config
WHERE key IN ('driver_price', 'max_driver_positions', 'cashback_rate_pct');