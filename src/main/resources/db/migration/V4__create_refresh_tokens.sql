CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL,
    device_info  VARCHAR(255),
    ip_address   VARCHAR(45),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rt_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_rt_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_rt_expires_at ON refresh_tokens (expires_at);
