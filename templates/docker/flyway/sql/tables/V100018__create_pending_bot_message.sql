CREATE TABLE pending_bot_message (
    message_id VARCHAR(36) PRIMARY KEY REFERENCES chat_message(id) ON DELETE CASCADE,
    room_target VARCHAR(255) NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_pending_bot_message_due ON pending_bot_message(available_at);