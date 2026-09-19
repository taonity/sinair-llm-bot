ALTER TABLE outbound_message ADD COLUMN trigger_message_id VARCHAR(36);
CREATE INDEX idx_outbound_room_created ON outbound_message(room_target, created_at);