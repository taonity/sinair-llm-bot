-- Runtime-tunable LLM/bot configuration overrides edited through the data console.
-- Each row overrides a single dotted config key (e.g. 'app.bot.decision.cooldown-seconds').
-- The yaml @ConfigurationProperties remain the defaults; a row present here overlays that key,
-- and deleting a row resets the key back to its yaml default. Only safe behavioural knobs are
-- stored here (never secrets like app.llm.api-key).

CREATE TABLE bot_config_override (
    config_key VARCHAR(200) PRIMARY KEY,
    value_json VARCHAR(20000) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(320) NOT NULL
);
