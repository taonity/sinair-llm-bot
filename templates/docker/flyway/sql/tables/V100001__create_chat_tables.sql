CREATE TABLE chat_message (
    id                  VARCHAR(36) PRIMARY KEY,
    dedup_key           VARCHAR(255) NOT NULL UNIQUE,
    room_target         VARCHAR(100) NOT NULL,
    sender_member_id    INT NOT NULL,
    sender_login        VARCHAR(100) NOT NULL,
    sender_color        VARCHAR(20),
    message_text        TEXT NOT NULL,
    message_style       VARCHAR(20) NOT NULL,
    recipient_member_id INT NOT NULL DEFAULT 0,
    sent_at             TIMESTAMP NOT NULL,
    received_at         TIMESTAMP NOT NULL
);

CREATE TABLE chat_event (
    id              VARCHAR(36) PRIMARY KEY,
    dedup_key       VARCHAR(255) NOT NULL UNIQUE,
    room_target     VARCHAR(100) NOT NULL,
    member_id       INT NOT NULL,
    user_id         INT NOT NULL DEFAULT 0,
    member_name     VARCHAR(100) NOT NULL,
    member_color    VARCHAR(20),
    status          VARCHAR(20) NOT NULL,
    event_data      VARCHAR(500),
    is_girl         BOOLEAN NOT NULL DEFAULT FALSE,
    is_moder        BOOLEAN NOT NULL DEFAULT FALSE,
    is_owner        BOOLEAN NOT NULL DEFAULT FALSE,
    event_time      TIMESTAMP NOT NULL,
    received_at     TIMESTAMP NOT NULL
);

CREATE INDEX idx_chat_message_room ON chat_message(room_target);
CREATE INDEX idx_chat_message_sent_at ON chat_message(sent_at);
CREATE INDEX idx_chat_event_room ON chat_event(room_target);
CREATE INDEX idx_chat_event_time ON chat_event(event_time);
