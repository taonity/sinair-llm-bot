-- Persisted bot pipeline traces: one row per processing run, retained like chat data so the
-- console can show every decision (and any alternatives) behind a bot reply or silence.

CREATE TABLE pipeline_run (
    id                      VARCHAR(36) PRIMARY KEY,
    pipeline_key            VARCHAR(50) NOT NULL,
    room_target             VARCHAR(100) NOT NULL,
    trigger_message_id      VARCHAR(36),
    trigger_sender_login    VARCHAR(150) NOT NULL,
    trigger_text            TEXT NOT NULL,
    outcome                 VARCHAR(30) NOT NULL,
    outcome_detail          VARCHAR(255),
    outbound_message_id     VARCHAR(36),
    stages_json             TEXT NOT NULL,
    created_at              TIMESTAMP NOT NULL
);

CREATE INDEX idx_pipeline_run_created ON pipeline_run(created_at);
CREATE INDEX idx_pipeline_run_room_created ON pipeline_run(room_target, created_at);
CREATE INDEX idx_pipeline_run_trigger_message ON pipeline_run(trigger_message_id);
