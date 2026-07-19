-- Link each rolling summary (current + archived versions) to the pipeline run that produced it.
-- That run holds the source transcript (raw messages); it is purged after 7 days by retention while
-- the compressed summary text is kept. The console uses this link to show the source while it exists.

ALTER TABLE room_summary ADD COLUMN pipeline_run_id VARCHAR(36);
ALTER TABLE room_summary_history ADD COLUMN pipeline_run_id VARCHAR(36);
