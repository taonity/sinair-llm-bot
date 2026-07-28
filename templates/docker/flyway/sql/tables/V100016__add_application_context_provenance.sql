CREATE TABLE bot_config_revision (
    id VARCHAR(36) PRIMARY KEY,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    effective_config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE pipeline_run ADD COLUMN config_revision_id VARCHAR(36);
ALTER TABLE pipeline_run ADD COLUMN context_manifest_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE chat_message ADD COLUMN source_outbound_message_id VARCHAR(36);
ALTER TABLE chat_message ADD COLUMN source_outbound_match VARCHAR(32);

CREATE INDEX idx_pipeline_run_outbound_message ON pipeline_run(outbound_message_id);
CREATE INDEX idx_pipeline_run_config_revision ON pipeline_run(config_revision_id);
CREATE INDEX idx_chat_message_source_outbound ON chat_message(source_outbound_message_id);
