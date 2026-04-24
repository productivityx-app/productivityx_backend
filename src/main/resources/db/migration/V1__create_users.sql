CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email                VARCHAR(255) NOT NULL,
    username             VARCHAR(30),
    phone                VARCHAR(20),
    password_hash        VARCHAR(255) NOT NULL,
    is_email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    gender               VARCHAR(10),
    birth_date           DATE,
    last_login_at        TIMESTAMPTZ,
    failed_login_count   INT          NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_phone    UNIQUE (phone)
);

CREATE INDEX idx_users_email    ON users (email);
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_is_active ON users (is_active);
