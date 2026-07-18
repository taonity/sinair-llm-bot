-- Track per-run LLM cost on pipeline traces: total tokens spent and, per call, which model
-- answered and the server-tool set it was offered (e.g. web_search). Shown in the console
-- "Pipelines" tab alongside each run.

ALTER TABLE pipeline_run ADD COLUMN total_tokens INT NOT NULL DEFAULT 0;
ALTER TABLE pipeline_run ADD COLUMN llm_usage_json TEXT NOT NULL DEFAULT '[]';
