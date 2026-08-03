package org.taonity.sinairllmbot.config.dto

import tools.jackson.databind.JsonNode

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
    val resettable: Boolean,
)

data class ConfigSchemaDto(
    val fields: List<ConfigFieldDto>,
    val tiers: List<TierInfoDto>,
)

data class TierInfoDto(
    val name: String,
    val custom: Boolean,
    val definedInProperties: Boolean,
    val definedInDatabase: Boolean,
    val shadowsDeployedTier: Boolean,
)

data class UpdateConfigBody(
    val values: Map<String, JsonNode>,
)

data class CreateTierBody(
    val name: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
)
