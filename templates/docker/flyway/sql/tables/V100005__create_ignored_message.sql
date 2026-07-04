CREATE TABLE ignored_message (
    id          VARCHAR(36) PRIMARY KEY,
    dedup_key   VARCHAR(255) NOT NULL UNIQUE,
    room_target VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_ignored_message_created_at ON ignored_message(created_at);
