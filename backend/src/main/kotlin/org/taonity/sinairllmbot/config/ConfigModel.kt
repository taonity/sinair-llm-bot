package org.taonity.sinairllmbot.config

import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.console.config.ConsolePagingProperties

/** The merged effective configuration: yaml defaults overlaid with DB overrides. */
data class EffectiveConfig(
    val bot: BotProperties,
    val llm: LlmProperties,
    val ingestion: IngestionProperties,
    val retention: RetentionProperties,
    val console: ConsolePagingProperties,
)

/** The value kind of a tunable field, driving parsing, validation and UI rendering. */
enum class ConfigType {
    BOOL,
    INT,
    LONG,
    DOUBLE,
    STRING,
    TEXT,
    ENUM,
    STRING_LIST,
}

/**
 * A single tunable configuration field. It knows how to read its current value out of an
 * [EffectiveConfig] and how to apply an already-parsed typed value back onto one (via immutable
 * `copy`). The same catalog drives the merge, boundary validation and the UI schema.
 */
class ConfigField(
    val key: String,
    val group: String,
    val type: ConfigType,
    val min: Double? = null,
    val max: Double? = null,
    val enumValues: (EffectiveConfig) -> List<String> = { emptyList() },
    val read: (EffectiveConfig) -> Any?,
    val apply: (EffectiveConfig, Any) -> EffectiveConfig,
    /**
     * Optional field-specific validation run after the generic type/range/enum/blank checks, e.g.
     * to require placeholder tokens in a templated prompt. Receives the already-parsed typed value
     * and must throw [ConfigValidationException] to reject it.
     */
    val validate: (Any) -> Unit = {},
) {
    /** Human label derived from the last path segment (tier name lives in [group]). */
    val label: String = key.substringAfterLast('.')
}

/** Thrown when a submitted override value is the wrong type, out of range or an invalid enum. */
class ConfigValidationException(message: String) : RuntimeException(message)
