-- V16: Soporte para el plan Luxtrox Zenith (ver docs/domain-model.md
-- seccion 7 -- adenda de Fase 6, escrita DESPUES de que el resto del
-- documento ya estaba aprobado).
--
-- Zenith es un producto separado de Driver: no genera InvestmentPosition,
-- no recibe rendimiento mensual, solo paga una licencia inicial + una
-- renovacion anual fija de $250.

-- 1) Purchases ahora distingue que plan se compro.
ALTER TABLE purchases
    ADD COLUMN plan_type VARCHAR(10) NOT NULL DEFAULT 'DRIVER'
        CHECK (plan_type IN ('DRIVER', 'ZENITH'));

-- Quita el default despues de aplicarlo a las filas existentes -- las
-- compras nuevas deben especificar el plan explicitamente.
ALTER TABLE purchases ALTER COLUMN plan_type DROP DEFAULT;

-- 2) cashback_transactions.position_id pasa a ser NULLABLE: el nuevo
-- tipo REFERRAL_BONUS_DIRECT paga directo a available_balance sin
-- pasar por ninguna posicion (ver docs/domain-model.md 7.2). Como ya
-- no hay position.user de donde derivar el destinatario en ese caso,
-- se agrega user_id directo a la tabla (NULL salvo cuando
-- position_id tambien es NULL).
ALTER TABLE cashback_transactions ALTER COLUMN position_id DROP NOT NULL;
ALTER TABLE cashback_transactions ADD COLUMN user_id UUID REFERENCES users(id);

ALTER TABLE cashback_transactions DROP CONSTRAINT cashback_transactions_type_check;
ALTER TABLE cashback_transactions ADD CONSTRAINT cashback_transactions_type_check
    CHECK (type IN (
        'MONTHLY_PERFORMANCE', 'MONTHLY_PERFORMANCE_REASSIGNED',
        'REFERRAL_BONUS', 'REFERRAL_BONUS_DIRECT'
    ));

-- Consistencia: REFERRAL_BONUS_DIRECT nunca lleva position_id pero
-- SIEMPRE lleva user_id (es la unica forma de saber quien recibe el
-- dinero); cualquier otro tipo es lo opuesto -- siempre position_id,
-- nunca user_id (se deriva de position.user_id, no se duplica).
ALTER TABLE cashback_transactions ADD CONSTRAINT chk_referral_direct_no_position
    CHECK (
        (type = 'REFERRAL_BONUS_DIRECT' AND position_id IS NULL AND user_id IS NOT NULL)
        OR (type <> 'REFERRAL_BONUS_DIRECT' AND position_id IS NOT NULL AND user_id IS NULL)
    );

CREATE INDEX idx_cashback_tx_user ON cashback_transactions(user_id);

-- 3) Licencias de Zenith -- una por compra confirmada.
CREATE TABLE zenith_licenses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    purchase_id         UUID NOT NULL UNIQUE REFERENCES purchases(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXPIRED')),
    activated_at        TIMESTAMPTZ NOT NULL,
    current_period_end  TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_zenith_licenses_user ON zenith_licenses(user_id);
CREATE INDEX idx_zenith_licenses_status ON zenith_licenses(status);

-- 4) Pagos de renovacion anual -- siempre $250 fijos.
CREATE TABLE zenith_renewal_payments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    license_id    UUID NOT NULL REFERENCES zenith_licenses(id),
    amount        DECIMAL(14,2) NOT NULL CHECK (amount = 250.00),
    period_start  TIMESTAMPTZ NOT NULL,
    period_end    TIMESTAMPTZ NOT NULL,
    paid_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_zenith_renewals_license ON zenith_renewal_payments(license_id);

-- 5) RLS en las tablas nuevas (mismo criterio que V15 -- bloquea por
-- completo el acceso publico via PostgREST, sin policies).
ALTER TABLE zenith_licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE zenith_renewal_payments ENABLE ROW LEVEL SECURITY;

-- 6) Referrals necesita saber QUE compra del referido lo califico --
-- sin esto no hay forma de saber si la comision es 9% (Driver) o 40%
-- (Zenith) en el momento de pagar, que puede ser DESPUES de que el
-- referido ya compro (si el referente recien se vuelve elegible).
ALTER TABLE referrals ADD COLUMN triggering_purchase_id UUID REFERENCES purchases(id);
