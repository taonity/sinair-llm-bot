CREATE TABLE room_bot_state (
    room_target VARCHAR(100) PRIMARY KEY,
    muted       BOOLEAN NOT NULL DEFAULT FALSE,
    asleep      BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMP NOT NULL
);
