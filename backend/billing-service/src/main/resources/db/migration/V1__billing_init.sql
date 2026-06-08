-- Dictionaries (id INT PK, name UNIQUE)
CREATE TABLE sub_tiers      (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE sub_statuses   (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE txn_statuses   (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE txn_types      (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);
CREATE TABLE discount_types (id INT PRIMARY KEY, name VARCHAR(50) UNIQUE NOT NULL);

CREATE TABLE plans (
    id            INT PRIMARY KEY,
    code          VARCHAR(50) UNIQUE NOT NULL,
    tier_id       INT NOT NULL REFERENCES sub_tiers(id),
    duration_days INT NOT NULL CHECK (duration_days > 0),
    price_amount  DECIMAL(10,2) NOT NULL CHECK (price_amount >= 0),
    currency      CHAR(3) NOT NULL DEFAULT 'RUB',
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    is_public     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE billing_accounts (
    user_id    UUID PRIMARY KEY,
    trial_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    version    INT NOT NULL DEFAULT 0
);

CREATE TABLE subscriptions (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL UNIQUE,
    tier_id    INT NOT NULL REFERENCES sub_tiers(id),
    status_id  INT NOT NULL REFERENCES sub_statuses(id),
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_subscriptions_status_expires ON subscriptions (status_id, expires_at);

CREATE TABLE promo_codes (
    id               UUID PRIMARY KEY,
    code             VARCHAR(64) UNIQUE NOT NULL,
    discount_type_id INT NOT NULL REFERENCES discount_types(id),
    discount_value   DECIMAL(10,2) NOT NULL CHECK (discount_value > 0),
    max_uses         INT NOT NULL CHECK (max_uses >= 1),
    used_count       INT NOT NULL DEFAULT 0 CHECK (used_count <= max_uses),
    valid_from       TIMESTAMPTZ,
    valid_until      TIMESTAMPTZ,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL,
    version          INT NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    plan_id             INT REFERENCES plans(id),
    type_id             INT NOT NULL REFERENCES txn_types(id),
    status_id           INT NOT NULL REFERENCES txn_statuses(id),
    base_amount         DECIMAL(10,2) NOT NULL,
    discount_amount     DECIMAL(10,2) NOT NULL DEFAULT 0,
    amount              DECIMAL(10,2) NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'RUB',
    promo_code_id       UUID REFERENCES promo_codes(id),
    provider            VARCHAR(50) NOT NULL,
    provider_payment_id VARCHAR(255) UNIQUE,
    idempotency_key     VARCHAR(255) UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_transactions_user_created   ON transactions (user_id, created_at);
CREATE INDEX idx_transactions_status_created ON transactions (status_id, created_at);

CREATE TABLE promo_redemptions (
    promo_code_id  UUID NOT NULL REFERENCES promo_codes(id),
    user_id        UUID NOT NULL,
    transaction_id UUID REFERENCES transactions(id),
    redeemed_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (promo_code_id, user_id)
);
CREATE INDEX idx_promo_redemptions_user ON promo_redemptions (user_id);

CREATE TABLE billing_outbox_events (
    id         UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload    JSONB NOT NULL,
    status     VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_billing_outbox_status ON billing_outbox_events (status);
