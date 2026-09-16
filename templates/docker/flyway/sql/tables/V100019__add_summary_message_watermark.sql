ALTER TABLE room_summary ADD COLUMN last_message_received_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE room_summary ADD COLUMN last_message_id VARCHAR(36);
CREATE INDEX idx_chat_message_room_received ON chat_message(room_target, received_at, id);