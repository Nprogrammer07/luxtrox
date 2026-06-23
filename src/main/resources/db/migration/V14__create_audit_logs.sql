-- V14: Audit Logs. Toda operacion financiera debe registrar quien,
-- cuando, que entidad, valor anterior y valor nuevo (ver
-- docs/domain-model.md principio 6 y entidad 2.15).
CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(id),
    entity_type  VARCHAR(60) NOT NULL,
    entity_id    UUID NOT NULL,
    action       VARCHAR(60) NOT NULL,
    old_value    JSONB,
    new_value    JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
