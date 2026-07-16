-- V28: Luxtrox Genius pasa de matrícula anual de $200 a PAGO ÚNICO de $89
-- con acceso indefinido.
--
-- Estrategia "tapar sin borrar": la maquinaria de renovación anual
-- (plus_renewal_payments, current_period_end, renew()) se conserva pero
-- deja de usarse. Solo se ajustan los valores para que no estorben.

-- 1. Quitar el CHECK que forzaba amount = 200.00 en renovaciones.
--    Se reemplaza por 89.00 por si en el futuro se reactivan las
--    renovaciones; mientras tanto la tabla simplemente no recibe filas.
ALTER TABLE plus_renewal_payments DROP CONSTRAINT IF EXISTS plus_renewal_payments_amount_check;
ALTER TABLE plus_renewal_payments ADD CONSTRAINT plus_renewal_payments_amount_check
    CHECK (amount = 89.00);

-- 2. Acceso indefinido para las licencias Genius existentes.
--    Se usa una fecha muy lejana en vez de NULL para no romper el
--    NOT NULL de la columna ni la lógica que la lee.
UPDATE plus_licenses
SET current_period_end = TIMESTAMPTZ '9999-12-31 23:59:59+00'
WHERE status = 'ACTIVE';