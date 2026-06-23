-- V6: Referrals. Un registro por usuario referido (unique). El bono de
-- $100 solo se paga cuando referente Y referido cumplen sus condiciones
-- -- puede quedar QUALIFIED_AWAITING_REFERRER si el referente todavia
-- no tiene ninguna posicion propia (ver docs/domain-model.md 3.3 y 4.2).
CREATE TABLE referrals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_user_id    UUID NOT NULL REFERENCES users(id),
    referred_user_id    UUID NOT NULL UNIQUE REFERENCES users(id),
    referral_code_used  VARCHAR(20) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_PURCHASE'
        CHECK (status IN ('PENDING_PURCHASE', 'QUALIFIED_AWAITING_REFERRER', 'BONUS_PAID')),
    qualified_at        TIMESTAMPTZ,
    bonus_paid_at       TIMESTAMPTZ,
    target_position_id  UUID REFERENCES investment_positions(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_referrals_referrer ON referrals(referrer_user_id);
CREATE INDEX idx_referrals_status ON referrals(status);
