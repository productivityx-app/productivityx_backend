-- Device registry: tracks active devices per user for session management.
-- Each (user_id, device_id) pair is unique — the same deviceId on a new
-- login updates the existing row (upsert via ON CONFLICT).

CREATE TABLE user_devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id       VARCHAR(255) NOT NULL,
    device_name     VARCHAR(255),
    platform        VARCHAR(20) NOT NULL,
    push_token      TEXT,
    last_seen_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_last_seen ON user_devices(user_id, last_seen_at DESC);

-- Link refresh tokens to devices so revoking a device also revokes its sessions.
ALTER TABLE refresh_tokens ADD COLUMN device_id VARCHAR(255);
CREATE INDEX idx_refresh_tokens_device ON refresh_tokens(device_id);