-- Bot delivery queue and rolling room summaries.

CREATE TABLE outbound_message (
    id                      VARCHAR(36) PRIMARY KEY,
    room_target             VARCHAR(100) NOT NULL,
    message_text            TEXT NOT NULL,
    reply_to_external_id    VARCHAR(100),
    status                  VARCHAR(20) NOT NULL,
    created_at              TIMESTAMP NOT NULL,
    claimed_at              TIMESTAMP,
    sent_at                 TIMESTAMP
);

CREATE TABLE room_summary (
    id              VARCHAR(36) PRIMARY KEY,
    room_target     VARCHAR(100) NOT NULL UNIQUE,
    summary         TEXT NOT NULL,
    message_count   INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_outbound_status_created ON outbound_message(status, created_at);
CREATE INDEX idx_outbound_room_status ON outbound_message(room_target, status);
