-- V2: Users. available_balance es el saldo liquido retirable,
-- denormalizado por rendimiento (ver docs/domain-model.md seccion 2.2).
CREATE TABLE users (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name                 VARCHAR(150) NOT NULL,
    email                     VARCHAR(150) NOT NULL UNIQUE,
    phone                     VARCHAR(30)  NOT NULL,
    password_hash             VARCHAR(255) NOT NULL,
    role_id                   UUID NOT NULL REFERENCES roles(id),
    referral_code             VARCHAR(20)  NOT NULL UNIQUE,
    referred_by_user_id       UUID REFERENCES users(id),
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    available_balance         DECIMAL(14,2) NOT NULL DEFAULT 0
        CHECK (available_balance >= 0),
    total_packages_purchased  INT NOT NULL DEFAULT 0
        CHECK (total_packages_purchased >= 0 AND total_packages_purchased <= 30),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_referred_by ON users(referred_by_user_id);
CREATE INDEX idx_users_role ON users(role_id);
