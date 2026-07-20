-- User-created LLM tiers added through the data console (default tiers stay in yaml under
-- app.llm.tiers). Each row seeds a new tier's base model/temperature/max-tokens; per-field tweaks
-- afterwards are stored as normal bot_config_override rows keyed 'app.llm.tiers.<name>.<field>'.
-- Deleting a row removes the custom tier (its override rows are removed alongside it). The model is
-- verified against OpenRouter's /models catalogue before a row is ever inserted.

CREATE TABLE bot_config_tier (
    name        VARCHAR(50) PRIMARY KEY,
    model       VARCHAR(200) NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    max_tokens  INT NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    created_by  VARCHAR(320) NOT NULL
);
