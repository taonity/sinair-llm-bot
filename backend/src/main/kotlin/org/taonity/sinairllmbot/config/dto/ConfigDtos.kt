package org.taonity.sinairllmbot.config.dto

import tools.jackson.databind.JsonNode

/** One tunable field: its metadata, default and current effective value, for the console UI. */
data class ConfigFieldDto(
    val key: String,
    val group: String,
    val label: String,
    val type: String,
    val min: Double?,
    val max: Double?,
    val enumValues: List<String>,
    val defaultValue: Any?,
    val value: Any?,
    val overridden: Boolean,
)

data class ConfigSchemaDto(
    val fields: List<ConfigFieldDto>,
    /** Every tier currently in effect; `custom` tiers (added via the console) can be deleted. */
    val tiers: List<TierInfoDto>,
)

/** A tier in the effective config. Built-in (yaml) tiers have `custom=false` and cannot be removed. */
data class TierInfoDto(
    val name: String,
    val custom: Boolean,
)

/** Batch of key -> raw JSON value overrides submitted from the console. */
data class UpdateConfigBody(
    val values: Map<String, JsonNode>,
)

/** Payload to create a new custom tier; the model is verified against OpenRouter before it is saved. */
data class CreateTierBody(
    val name: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
)
