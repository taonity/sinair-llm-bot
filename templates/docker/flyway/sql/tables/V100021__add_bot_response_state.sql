ALTER TABLE chat_message ADD COLUMN bot_response_status VARCHAR(32);
ALTER TABLE chat_message ADD COLUMN bot_response_reason VARCHAR(64);
ALTER TABLE chat_message ADD COLUMN bot_response_detail TEXT;
ALTER TABLE chat_message ADD COLUMN bot_response_category VARCHAR(64);
ALTER TABLE chat_message ADD COLUMN bot_response_deferred_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chat_message ADD COLUMN bot_response_next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chat_message ADD COLUMN bot_response_updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chat_message ADD COLUMN bot_response_outbound_message_id VARCHAR(36);