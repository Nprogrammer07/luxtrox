-- V1: Roles. Tabla separada (no enum) para permitir agregar roles a
-- futuro sin migracion de tipo. Seed inicial: ADMIN y USER (sin
-- SUPER_ADMIN -- ADMIN ya tiene control total, ver docs/domain-model.md).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(30)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Control total de la plataforma'),
    ('USER',  'Usuario inversor estandar');
