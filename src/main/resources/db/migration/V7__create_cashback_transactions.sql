-- V7: Cashback Transactions. Bitacora INMUTABLE de cada movimiento de
-- cashback hacia una posicion -- nunca se actualiza ni se borra una
-- fila ya creada, solo se insertan nuevas (ver docs/domain-model.md 2.6).
CREATE TABLE cashback_transactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id                 UUID NOT NULL REFERENCES investment_positions(id),
    type                        VARCHAR(40) NOT NULL
        CHECK (type IN ('MONTHLY_PERFORMANCE', 'MONTHLY_PERFORMANCE_REASSIGNED', 'REFERRAL_BONUS')),
    amount                      DECIMAL(14,2) NOT NULL CHECK (amount > 0),
    effective_rate              DECIMAL(5,2),
    source_performance_id       UUID REFERENCES monthly_performances(id),
    source_referral_id          UUID REFERENCES referrals(id),
    reassigned_from_position_id UUID REFERENCES investment_positions(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cashback_tx_position ON cashback_transactions(position_id);
CREATE INDEX idx_cashback_tx_performance ON cashback_transactions(source_performance_id);
CREATE INDEX idx_cashback_tx_referral ON cashback_transactions(source_referral_id);
