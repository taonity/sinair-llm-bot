-- Data console: per-user access control + audit trail.

ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE app_user ADD COLUMN access_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE app_user ADD COLUMN requested_role VARCHAR(20);

CREATE TABLE audit_log (
    id              VARCHAR(36) PRIMARY KEY,
    action          VARCHAR(40) NOT NULL,
    target_type     VARCHAR(40) NOT NULL,
    target_id       VARCHAR(64),
    actor_google_id VARCHAR NOT NULL,
    actor_email     VARCHAR NOT NULL,
    occurred_at     TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
