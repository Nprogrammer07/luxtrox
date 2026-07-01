-- V24: Tokens de reset de contraseña. Cada solicitud de "olvidé mi
-- contraseña" genera un token UUID de uso unico con 24h de vigencia.
-- El token se invalida al usarlo (deleted_at se pone NULL).
CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      UUID        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_token ON password_reset_tokens(token);