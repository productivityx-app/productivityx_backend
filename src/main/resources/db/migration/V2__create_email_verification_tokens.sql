CREATE TABLE email_verification_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL,
    otp          VARCHAR(6)  NOT NULL,
    otp_attempts INT         NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evt_user_id   ON email_verification_tokens (user_id);
CREATE INDEX idx_evt_token_hash ON email_verification_tokens (token_hash);
