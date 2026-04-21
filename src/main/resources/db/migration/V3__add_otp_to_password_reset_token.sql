ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS otp           VARCHAR(6)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS otp_attempts  INT         NOT NULL DEFAULT 0;