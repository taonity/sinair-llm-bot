-- Track how resilient each pipeline run's JSON-only prompts (triage, critic) were: how many
-- attempts returned output that couldn't be deserialized (json_parse_failure_count) and the raw
-- offending payloads (json_parse_failures_json). Shown in the console "Pipelines" tab so an
-- operator can see malformed-JSON frequency and inspect exactly what the model returned.

ALTER TABLE pipeline_run ADD COLUMN json_parse_failure_count INT NOT NULL DEFAULT 0;
ALTER TABLE pipeline_run ADD COLUMN json_parse_failures_json TEXT NOT NULL DEFAULT '[]';
