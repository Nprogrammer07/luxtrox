-- V9: Detalles de retiro -- tabla base + 2 tablas hijas en 1:1 (en vez
-- de una sola tabla con columnas nullable) para mejor trazabilidad y
-- seguridad de los datos sensibles bancarios vs. cripto
-- (ver docs/domain-model.md 2.9 / 2.10, decision explicita del cliente).
CREATE TABLE crypto_withdrawal_details (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_request_id  UUID NOT NULL UNIQUE REFERENCES withdrawal_requests(id),
    full_name              VARCHAR(150) NOT NULL,
    email                  VARCHAR(150) NOT NULL,
    phone                  VARCHAR(30)  NOT NULL,
    blockchain_network     VARCHAR(30)  NOT NULL,
    wallet_address         VARCHAR(255) NOT NULL,
    transaction_hash       VARCHAR(255)
);

CREATE TABLE bank_withdrawal_details (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_request_id  UUID NOT NULL UNIQUE REFERENCES withdrawal_requests(id),
    full_name              VARCHAR(150) NOT NULL,
    email                  VARCHAR(150) NOT NULL,
    phone                  VARCHAR(30)  NOT NULL,
    country                VARCHAR(80)  NOT NULL,
    bank_name              VARCHAR(120) NOT NULL,
    account_type           VARCHAR(20)  NOT NULL CHECK (account_type IN ('SAVINGS', 'CHECKING')),
    account_number         VARCHAR(50)  NOT NULL,
    account_holder_name    VARCHAR(150) NOT NULL,
    document_id            VARCHAR(50)  NOT NULL
);
