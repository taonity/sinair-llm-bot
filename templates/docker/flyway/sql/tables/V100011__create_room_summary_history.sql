-- Archive of superseded rolling room summaries, pruned to the latest few versions per room.

CREATE TABLE room_summary_history (
    id              VARCHAR(36) PRIMARY KEY,
    room_target     VARCHAR(100) NOT NULL,
    summary         TEXT NOT NULL,
    message_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_room_summary_history_room_created ON room_summary_history(room_target, created_at);
