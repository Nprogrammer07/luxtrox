-- V5: Monthly Performance. Un solo rendimiento por mes calendario.
-- applied_at NULL = registrado pero todavia no distribuido a las
-- posiciones (ver docs/domain-model.md 2.5 y algoritmo 4.1).
CREATE TABLE monthly_performances (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month                  INT NOT NULL CHECK (month BETWEEN 1 AND 12),
    year                   INT NOT NULL CHECK (year >= 2024),
    percentage             DECIMAL(5,2) NOT NULL CHECK (percentage > 0),
    registered_by_admin_id UUID NOT NULL REFERENCES users(id),
    applied_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (month, year)
);
