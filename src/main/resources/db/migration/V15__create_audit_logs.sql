CREATE TABLE audit_logs (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID,
    event      VARCHAR(40) NOT NULL,
    ip_address VARCHAR(45),
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id    ON audit_logs (user_id);
CREATE INDEX idx_audit_event      ON audit_logs (event);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
