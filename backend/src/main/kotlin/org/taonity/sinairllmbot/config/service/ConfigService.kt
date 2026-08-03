package org.taonity.sinairllmbot.config.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.ConfigField
import org.taonity.sinairllmbot.config.ConfigRegistry
import org.taonity.sinairllmbot.config.ConfigValidationException
import org.taonity.sinairllmbot.config.dto.ConfigFieldDto
import org.taonity.sinairllmbot.config.dto.ConfigSchemaDto
import org.taonity.sinairllmbot.config.dto.CreateTierBody
import org.taonity.sinairllmbot.config.dto.TierInfoDto
import org.taonity.sinairllmbot.config.entity.BotConfigOverrideEntity
import org.taonity.sinairllmbot.config.entity.BotConfigTierEntity
import org.taonity.sinairllmbot.config.repository.BotConfigOverrideRepository
import org.taonity.sinairllmbot.config.repository.BotConfigTierRepository
import org.taonity.sinairllmbot.console.entity.AuditAction
import org.taonity.sinairllmbot.console.exception.ConsoleNotFoundException
import org.taonity.sinairllmbot.console.service.AccessGuard
import org.taonity.sinairllmbot.console.service.AuditService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import tools.jackson.databind.JsonNode
import java.time.Instant

@Service
class ConfigService(
    private val registry: ConfigRegistry,
    private val settings: BotSettings,
    private val overrideRepository: BotConfigOverrideRepository,
    private val tierRepository: BotConfigTierRepository,
    private val modelVerifier: OpenRouterModelVerifier,
    private val accessGuard: AccessGuard,
    private val auditService: AuditService,
) {
    fun getSchema(principal: GoogleUserPrincipal): ConfigSchemaDto {
        accessGuard.requireView(principal)
        return buildSchema()
    }

    @Transactional
    fun update(principal: GoogleUserPrincipal, values: Map<String, JsonNode>): ConfigSchemaDto {
        val actor = accessGuard.requireOwner(principal)
        if (values.isEmpty()) throw ConfigValidationException("No values provided")

        // Validate everything (parsing onto a cumulatively-updated effective view so any
        // cross-field enum constraints see the batch) before writing a single row.
        var effective = settings.effective()
        val parsed = LinkedHashMap<String, Pair<ConfigField, Any>>()
        for ((key, node) in values) {
            val field = registry.field(key) ?: throw ConfigValidationException("Unknown config key '$key'")
            val typed = registry.parse(field, node, effective)
            effective = field.apply(effective, typed)
            parsed[key] = field to typed
        }

        val now = Instant.now()
        for ((key, pair) in parsed) {
            val json = registry.serialize(pair.second)
            val existing = overrideRepository.findById(key).orElse(null)
            if (existing != null) {
                existing.valueJson = json
                existing.updatedAt = now
                existing.updatedBy = actor.email
                overrideRepository.save(existing)
            } else {
                overrideRepository.save(
                    BotConfigOverrideEntity(configKey = key, valueJson = json, updatedAt = now, updatedBy = actor.email),
                )
            }
            auditService.record(AuditAction.EDIT_CONFIG, "bot_config_override", key, actor)
        }
        settings.reload()
        return buildSchema()
    }

    @Transactional
    fun reset(principal: GoogleUserPrincipal, key: String): ConfigSchemaDto {
        val actor = accessGuard.requireOwner(principal)
        registry.field(key) ?: throw ConsoleNotFoundException("Unknown config key '$key'")
        if (overrideRepository.existsById(key)) {
            overrideRepository.deleteById(key)
            auditService.record(AuditAction.RESET_CONFIG, "bot_config_override", key, actor)
            settings.reload()
        }
        return buildSchema()
    }

    @Transactional
    fun addTier(principal: GoogleUserPrincipal, body: CreateTierBody): ConfigSchemaDto {
        val actor = accessGuard.requireOwner(principal)

        val name = body.name.trim()
        if (!TIER_NAME_REGEX.matches(name)) {
            throw ConfigValidationException(
                "Invalid tier name: use 2-50 lowercase letters, digits or hyphens, starting with a letter",
            )
        }
        if (name in settings.effective().llm.tiers.keys) {
            throw ConfigValidationException("Tier '$name' already exists")
        }

        val model = body.model.trim()
        if (model.isBlank()) throw ConfigValidationException("Model must not be blank")
        if (body.temperature < 0.0 || body.temperature > 2.0) {
            throw ConfigValidationException("Temperature must be between 0 and 2")
        }
        if (body.maxTokens < 1 || body.maxTokens > 8000) {
            throw ConfigValidationException("Max tokens must be between 1 and 8000")
        }

        modelVerifier.verify(model)

        tierRepository.save(
            BotConfigTierEntity(
                name = name,
                model = model,
                temperature = body.temperature,
                maxTokens = body.maxTokens,
                createdAt = Instant.now(),
                createdBy = actor.email,
            ),
        )
        auditService.record(AuditAction.ADD_TIER, "bot_config_tier", name, actor)
        settings.reload()
        return buildSchema()
    }

    @Transactional
    fun deleteTier(principal: GoogleUserPrincipal, name: String): ConfigSchemaDto {
        val actor = accessGuard.requireOwner(principal)
        if (!tierRepository.existsById(name)) {
            throw ConsoleNotFoundException("Custom tier '$name' not found")
        }

        if (name !in settings.deployedDefaults().llm.tiers) {
            val effective = settings.effective()
            val references = buildList {
                if (effective.llm.activeReplyTier == name) add("active reply tier")
                if (effective.llm.gateTier == name) add("gate tier")
                if (effective.llm.criticTier == name) add("critic tier")
                if (effective.ingestion.visionTier == name) add("vision tier")
            }
            if (references.isNotEmpty()) {
                throw ConfigValidationException(
                    "Tier '$name' is still in use as the ${references.joinToString(", ")}. " +
                        "Point those to another tier before deleting it.",
                )
            }
        }

        // Drop any per-field overrides for this tier so no orphan rows linger.
        listOf("model", "temperature", "max-tokens")
            .map { "app.llm.tiers.$name.$it" }
            .filter { overrideRepository.existsById(it) }
            .forEach { overrideRepository.deleteById(it) }

        tierRepository.deleteById(name)
        auditService.record(AuditAction.DELETE_TIER, "bot_config_tier", name, actor)
        settings.reload()
        return buildSchema()
    }

    private fun buildSchema(): ConfigSchemaDto {
        val deployedDefaults = settings.deployedDefaults()
        val defaults = settings.defaults()
        val effective = settings.effective()
        val overriddenKeys = overrideRepository.findAll().mapTo(HashSet()) { it.configKey }
        val databaseTierNames = tierRepository.findAll().mapTo(HashSet()) { it.name }
        val deployedTierNames = deployedDefaults.llm.tiers.keys
        val tierNames = effective.llm.tiers.keys.toList()
        val fields = registry.fields(tierNames).map { field ->
            val tierName = tierName(field.key)
            val deployedValue = tierName
                ?.takeIf { it in deployedTierNames }
                ?.let { field.read(deployedDefaults) }
                ?: field.read(defaults)
            val value = field.read(effective)
            ConfigFieldDto(
                key = field.key,
                group = field.group,
                label = field.label,
                type = field.type.name,
                min = field.min,
                max = field.max,
                enumValues = field.enumValues(effective),
                defaultValue = deployedValue,
                value = value,
                overridden = field.key in overriddenKeys || deployedValue != value,
                resettable = field.key in overriddenKeys,
            )
        }
        val tiers = tierNames.map { name ->
            val definedInProperties = name in deployedTierNames
            val definedInDatabase = name in databaseTierNames
            TierInfoDto(
                name = name,
                custom = definedInDatabase && !definedInProperties,
                definedInProperties = definedInProperties,
                definedInDatabase = definedInDatabase,
                shadowsDeployedTier = definedInProperties && definedInDatabase,
            )
        }
        return ConfigSchemaDto(fields, tiers)
    }

    private fun tierName(key: String): String? = TIER_KEY_REGEX.matchEntire(key)?.groupValues?.get(1)

    private companion object {
        private val TIER_NAME_REGEX = Regex("^[a-z][a-z0-9-]{1,49}$")
        private val TIER_KEY_REGEX = Regex("^app\\.llm\\.tiers\\.([^.]+)\\.(?:model|temperature|max-tokens)$")
    }
}
