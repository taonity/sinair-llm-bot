ALTER TABLE outbound_message ADD COLUMN pipeline_run_id VARCHAR(36);

CREATE INDEX idx_pipeline_run_outbound ON pipeline_run(outbound_message_id);