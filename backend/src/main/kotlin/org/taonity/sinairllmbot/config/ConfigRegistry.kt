package org.taonity.sinairllmbot.config

import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.LlmProperties
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * The single source of truth for every runtime-tunable config field. The same catalog drives:
 *  - the merge (how to overlay a parsed value onto the effective config, via immutable `copy`),
 *  - boundary validation (type + range + enum membership),
 *  - the UI schema (`GET /console/config`).
 *
 * Tier fields (`app.llm.tiers.<name>.*`) are generated on demand for whatever tiers currently
 * exist (built-in yaml tiers plus any custom tiers added through the console), so the catalogue and
 * the merge stay in sync as tiers come and go. The tier-name enums (`active-reply-tier`, ...) read
 * the live tier set, so a new tier immediately becomes a selectable option.
 * Secrets/infra (api-key, base-url, ...) are deliberately absent so they can never be read or set
 * through the console.
 */
@Component
class ConfigRegistry(
    private val objectMapper: ObjectMapper,
) {
    private val tierNames: (EffectiveConfig) -> List<String> = { it.llm.tiers.keys.toList() }
    private val fieldsBeforeTiers: List<ConfigField> = buildFieldsBeforeTiers()
    private val fieldsAfterTiers: List<ConfigField> = buildFieldsAfterTiers()
    private val staticByKey: Map<String, ConfigField> =
        (fieldsBeforeTiers + fieldsAfterTiers).associateBy { it.key }

    /** The full ordered field catalogue for the given tier set (tier groups sit between the LLM
     * and Bot sections, one group per tier). */
    fun fields(tierNames: List<String>): List<ConfigField> =
        fieldsBeforeTiers + tierNames.flatMap { tierFields(it) } + fieldsAfterTiers

    /** Resolves a single field by key, generating tier fields on the fly for any tier name. */
    fun field(key: String): ConfigField? {
        staticByKey[key]?.let { return it }
        val match = TIER_KEY_REGEX.matchEntire(key) ?: return null
        return tierField(match.groupValues[1], match.groupValues[2])
    }

    /**
     * Parses a raw JSON value for [field] and validates its type, range and (for enums) membership
     * against [effective]. Returns the typed value ready for [ConfigField.apply].
     */
    fun parse(field: ConfigField, node: JsonNode, effective: EffectiveConfig): Any {
        val value: Any = try {
            when (field.type) {
                ConfigType.BOOL -> objectMapper.treeToValue(node, java.lang.Boolean::class.java) as Boolean
                ConfigType.INT -> objectMapper.treeToValue(node, Integer::class.java) as Int
                ConfigType.LONG -> objectMapper.treeToValue(node, java.lang.Long::class.java) as Long
                ConfigType.DOUBLE -> objectMapper.treeToValue(node, java.lang.Double::class.java) as Double
                ConfigType.STRING, ConfigType.TEXT, ConfigType.ENUM ->
                    objectMapper.treeToValue(node, String::class.java)
                ConfigType.STRING_LIST -> objectMapper.convertValue(node, STRING_LIST_TYPE)
            }
        } catch (e: Exception) {
            throw ConfigValidationException("Invalid value for '${field.key}': expected ${field.type}")
        }

        when (field.type) {
            ConfigType.INT, ConfigType.LONG, ConfigType.DOUBLE -> validateRange(field, (value as Number).toDouble())
            ConfigType.ENUM -> {
                val allowed = field.enumValues(effective)
                if (value !in allowed) {
                    throw ConfigValidationException("Invalid value for '${field.key}': must be one of $allowed")
                }
            }
            ConfigType.STRING, ConfigType.TEXT -> {
                if ((value as String).isBlank()) {
                    throw ConfigValidationException("Invalid value for '${field.key}': must not be blank")
                }
            }
            else -> Unit
        }
        field.validate(value)
        return value
    }

    /** Serializes a typed value to the JSON text stored in the override row. */
    fun serialize(value: Any): String = objectMapper.writeValueAsString(value)

    /** Reads a stored override row's JSON back into a typed value for [field]. */
    fun parseStored(field: ConfigField, valueJson: String, effective: EffectiveConfig): Any =
        parse(field, objectMapper.readTree(valueJson), effective)

    private fun validateRange(field: ConfigField, value: Double) {
        field.min?.let { if (value < it) throw ConfigValidationException("'${field.key}' must be >= ${format(it)}") }
        field.max?.let { if (value > it) throw ConfigValidationException("'${field.key}' must be <= ${format(it)}") }
    }

    private fun format(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun buildFieldsBeforeTiers(): List<ConfigField> {
        val fields = mutableListOf<ConfigField>()

        // ---- LLM ----
        fields += ConfigField(
            key = "app.llm.active-reply-tier", group = "LLM", type = ConfigType.ENUM, enumValues = tierNames,
            read = { it.llm.activeReplyTier },
            apply = { c, v -> c.copy(llm = c.llm.copy(activeReplyTier = v as String)) },
        )
        fields += ConfigField(
            key = "app.llm.gate-tier", group = "LLM", type = ConfigType.ENUM, enumValues = tierNames,
            read = { it.llm.gateTier },
            apply = { c, v -> c.copy(llm = c.llm.copy(gateTier = v as String)) },
        )
        fields += ConfigField(
            key = "app.llm.critic-tier", group = "LLM", type = ConfigType.ENUM, enumValues = tierNames,
            read = { it.llm.criticTier },
            apply = { c, v -> c.copy(llm = c.llm.copy(criticTier = v as String)) },
        )
        fields += ConfigField(
            key = "app.llm.reply-web-search", group = "LLM", type = ConfigType.BOOL,
            read = { it.llm.replyWebSearch },
            apply = { c, v -> c.copy(llm = c.llm.copy(replyWebSearch = v as Boolean)) },
        )

        // ---- LLM · Critic ----
        fields += ConfigField(
            key = "app.llm.critic.enabled", group = "LLM · Critic", type = ConfigType.BOOL,
            read = { it.llm.critic.enabled },
            apply = { c, v -> c.copy(llm = c.llm.copy(critic = c.llm.critic.copy(enabled = v as Boolean))) },
        )
        fields += ConfigField(
            key = "app.llm.critic.candidate-count", group = "LLM · Critic", type = ConfigType.INT, min = 1.0, max = 8.0,
            read = { it.llm.critic.candidateCount },
            apply = { c, v -> c.copy(llm = c.llm.copy(critic = c.llm.critic.copy(candidateCount = v as Int))) },
        )
        fields += ConfigField(
            key = "app.llm.critic.candidate-temperature", group = "LLM · Critic", type = ConfigType.DOUBLE, min = 0.0, max = 2.0,
            read = { it.llm.critic.candidateTemperature },
            apply = { c, v -> c.copy(llm = c.llm.copy(critic = c.llm.critic.copy(candidateTemperature = v as Double))) },
        )
        fields += ConfigField(
            key = "app.llm.critic.repair-threshold", group = "LLM · Critic", type = ConfigType.INT, min = 0.0, max = 10.0,
            read = { it.llm.critic.repairThreshold },
            apply = { c, v -> c.copy(llm = c.llm.copy(critic = c.llm.critic.copy(repairThreshold = v as Int))) },
        )
        fields += ConfigField(
            key = "app.llm.critic.prompt", group = "LLM · Critic", type = ConfigType.TEXT,
            read = { it.llm.critic.prompt },
            apply = { c, v -> c.copy(llm = c.llm.copy(critic = c.llm.critic.copy(prompt = v as String))) },
            validate = { v -> requireCriticPlaceholders(v as String) },
        )

        return fields
    }

    /** The three tunable fields of a single tier (built-in or custom). */
    private fun tierFields(name: String): List<ConfigField> =
        listOf("model", "temperature", "max-tokens").mapNotNull { tierField(name, it) }

    private fun tierField(name: String, sub: String): ConfigField? {
        val group = "LLM · Tier: $name"
        return when (sub) {
            "model" -> ConfigField(
                key = "app.llm.tiers.$name.model", group = group, type = ConfigType.STRING,
                read = { it.llm.tiers[name]?.model },
                apply = { c, v -> c.copy(llm = c.llm.copy(tiers = updateTier(c.llm.tiers, name) { it.copy(model = v as String) })) },
            )
            "temperature" -> ConfigField(
                key = "app.llm.tiers.$name.temperature", group = group, type = ConfigType.DOUBLE, min = 0.0, max = 2.0,
                read = { it.llm.tiers[name]?.temperature },
                apply = { c, v -> c.copy(llm = c.llm.copy(tiers = updateTier(c.llm.tiers, name) { it.copy(temperature = v as Double) })) },
            )
            "max-tokens" -> ConfigField(
                key = "app.llm.tiers.$name.max-tokens", group = group, type = ConfigType.INT, min = 1.0, max = 8000.0,
                read = { it.llm.tiers[name]?.maxTokens },
                apply = { c, v -> c.copy(llm = c.llm.copy(tiers = updateTier(c.llm.tiers, name) { it.copy(maxTokens = v as Int) })) },
            )
            else -> null
        }
    }

    private fun buildFieldsAfterTiers(): List<ConfigField> {
        val fields = mutableListOf<ConfigField>()

        // ---- Bot · Decision ----
        fields += ConfigField(
            key = "app.bot.decision.debounce-seconds", group = "Bot · Decision", type = ConfigType.LONG, min = 0.0, max = 3600.0,
            read = { it.bot.decision.debounceSeconds },
            apply = { c, v -> c.copy(bot = c.bot.copy(decision = c.bot.decision.copy(debounceSeconds = v as Long))) },
        )
        fields += ConfigField(
            key = "app.bot.decision.cooldown-seconds", group = "Bot · Decision", type = ConfigType.LONG, min = 0.0, max = 86400.0,
            read = { it.bot.decision.cooldownSeconds },
            apply = { c, v -> c.copy(bot = c.bot.copy(decision = c.bot.decision.copy(cooldownSeconds = v as Long))) },
        )
        fields += ConfigField(
            key = "app.bot.decision.max-replies-per-window", group = "Bot · Decision", type = ConfigType.INT, min = 0.0, max = 1000.0,
            read = { it.bot.decision.maxRepliesPerWindow },
            apply = { c, v -> c.copy(bot = c.bot.copy(decision = c.bot.decision.copy(maxRepliesPerWindow = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.decision.window-minutes", group = "Bot · Decision", type = ConfigType.LONG, min = 1.0, max = 1440.0,
            read = { it.bot.decision.windowMinutes },
            apply = { c, v -> c.copy(bot = c.bot.copy(decision = c.bot.decision.copy(windowMinutes = v as Long))) },
        )
        fields += ConfigField(
            key = "app.bot.decision.spontaneous-probability", group = "Bot · Decision", type = ConfigType.DOUBLE, min = 0.0, max = 1.0,
            read = { it.bot.decision.spontaneousProbability },
            apply = { c, v -> c.copy(bot = c.bot.copy(decision = c.bot.decision.copy(spontaneousProbability = v as Double))) },
        )

        // ---- Bot · Context ----
        fields += ConfigField(
            key = "app.bot.context.recent-message-count", group = "Bot · Context", type = ConfigType.INT, min = 1.0, max = 200.0,
            read = { it.bot.context.recentMessageCount },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(recentMessageCount = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.context.summary-refresh-every-messages", group = "Bot · Context", type = ConfigType.INT, min = 1.0, max = 1000.0,
            read = { it.bot.context.summaryRefreshEveryMessages },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(summaryRefreshEveryMessages = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.context.max-summary-chars", group = "Bot · Context", type = ConfigType.INT, min = 100.0, max = 20000.0,
            read = { it.bot.context.maxSummaryChars },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(maxSummaryChars = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.context.summary-max-tokens", group = "Bot · Context", type = ConfigType.INT, min = 100.0, max = 32000.0,
            read = { it.bot.context.summaryMaxTokens },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(summaryMaxTokens = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.context.max-message-chars", group = "Bot · Context", type = ConfigType.INT, min = 100.0, max = 20000.0,
            read = { it.bot.context.maxMessageChars },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(maxMessageChars = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.context.session-gap-minutes", group = "Bot · Context", type = ConfigType.LONG, min = 1.0, max = 10080.0,
            read = { it.bot.context.sessionGapMinutes },
            apply = { c, v -> c.copy(bot = c.bot.copy(context = c.bot.context.copy(sessionGapMinutes = v as Long))) },
        )

        // ---- Bot · Persona ----
        fields += ConfigField(
            key = "app.bot.persona.name", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.name },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(name = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.language", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.language },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(language = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.prompt", group = "Bot · Persona", type = ConfigType.TEXT,
            read = { it.bot.persona.prompt },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(prompt = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.stop-command", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.stopCommand },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(stopCommand = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.start-command", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.startCommand },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(startCommand = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.sleep-command", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.sleepCommand },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(sleepCommand = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.wake-command", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.wakeCommand },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(wakeCommand = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.sleep-nick-suffix", group = "Bot · Persona", type = ConfigType.STRING,
            read = { it.bot.persona.sleepNickSuffix },
            apply = { c, v -> c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(sleepNickSuffix = v as String))) },
        )
        fields += ConfigField(
            key = "app.bot.persona.aliases", group = "Bot · Persona", type = ConfigType.STRING_LIST,
            read = { it.bot.persona.aliases },
            apply = { c, v ->
                @Suppress("UNCHECKED_CAST")
                c.copy(bot = c.bot.copy(persona = c.bot.persona.copy(aliases = v as List<String>)))
            },
        )

        // ---- Bot · Typing ----
        fields += ConfigField(
            key = "app.bot.typing.ttl-seconds", group = "Bot · Typing", type = ConfigType.LONG, min = 1.0, max = 600.0,
            read = { it.bot.typing.ttlSeconds },
            apply = { c, v -> c.copy(bot = c.bot.copy(typing = c.bot.typing.copy(ttlSeconds = v as Long))) },
        )

        // ---- Bot · Limits ----
        fields += ConfigField(
            key = "app.bot.limits.max-reply-chars", group = "Bot · Limits", type = ConfigType.INT, min = 100.0, max = 4000.0,
            read = { it.bot.limits.maxReplyChars },
            apply = { c, v -> c.copy(bot = c.bot.copy(limits = c.bot.limits.copy(maxReplyChars = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.limits.trace-trigger-text-max", group = "Bot · Limits", type = ConfigType.INT, min = 100.0, max = 20000.0,
            read = { it.bot.limits.traceTriggerTextMax },
            apply = { c, v -> c.copy(bot = c.bot.copy(limits = c.bot.limits.copy(traceTriggerTextMax = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.limits.event-scan-limit", group = "Bot · Limits", type = ConfigType.INT, min = 10.0, max = 1000.0,
            read = { it.bot.limits.eventScanLimit },
            apply = { c, v -> c.copy(bot = c.bot.copy(limits = c.bot.limits.copy(eventScanLimit = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.limits.summary-history-versions", group = "Bot · Limits", type = ConfigType.INT, min = 0.0, max = 100.0,
            read = { it.bot.limits.summaryHistoryVersions },
            apply = { c, v -> c.copy(bot = c.bot.copy(limits = c.bot.limits.copy(summaryHistoryVersions = v as Int))) },
        )
        fields += ConfigField(
            key = "app.bot.limits.link-context-messages", group = "Bot · Limits", type = ConfigType.INT, min = 0.0, max = 50.0,
            read = { it.bot.limits.linkContextMessages },
            apply = { c, v -> c.copy(bot = c.bot.copy(limits = c.bot.limits.copy(linkContextMessages = v as Int))) },
        )

        // ---- Ingestion ----
        fields += ConfigField(
            key = "app.ingestion.enabled", group = "Ingestion", type = ConfigType.BOOL,
            read = { it.ingestion.enabled },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(enabled = v as Boolean)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-urls-per-message", group = "Ingestion", type = ConfigType.INT, min = 0.0, max = 20.0,
            read = { it.ingestion.maxUrlsPerMessage },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxUrlsPerMessage = v as Int)) },
        )
        fields += ConfigField(
            key = "app.ingestion.fetch-timeout-seconds", group = "Ingestion", type = ConfigType.LONG, min = 1.0, max = 120.0,
            read = { it.ingestion.fetchTimeoutSeconds },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(fetchTimeoutSeconds = v as Long)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-redirects", group = "Ingestion", type = ConfigType.INT, min = 0.0, max = 20.0,
            read = { it.ingestion.maxRedirects },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxRedirects = v as Int)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-page-bytes", group = "Ingestion", type = ConfigType.LONG, min = 1000.0, max = 50000000.0,
            read = { it.ingestion.maxPageBytes },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxPageBytes = v as Long)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-context-chars", group = "Ingestion", type = ConfigType.INT, min = 100.0, max = 50000.0,
            read = { it.ingestion.maxContextChars },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxContextChars = v as Int)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-chars-per-source", group = "Ingestion", type = ConfigType.INT, min = 100.0, max = 50000.0,
            read = { it.ingestion.maxCharsPerSource },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxCharsPerSource = v as Int)) },
        )
        fields += ConfigField(
            key = "app.ingestion.max-doc-links", group = "Ingestion", type = ConfigType.INT, min = 1.0, max = 50.0,
            read = { it.ingestion.maxDocLinks },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(maxDocLinks = v as Int)) },
        )
        fields += ConfigField(
            key = "app.ingestion.vision-tier", group = "Ingestion", type = ConfigType.ENUM, enumValues = tierNames,
            read = { it.ingestion.visionTier },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(visionTier = v as String)) },
        )
        fields += ConfigField(
            key = "app.ingestion.image.max-bytes", group = "Ingestion", type = ConfigType.LONG, min = 1000.0, max = 50000000.0,
            read = { it.ingestion.image.maxBytes },
            apply = { c, v -> c.copy(ingestion = c.ingestion.copy(image = c.ingestion.image.copy(maxBytes = v as Long))) },
        )

        // ---- Retention ----
        fields += ConfigField(
            key = "app.retention.chat.retention-days", group = "Retention", type = ConfigType.LONG, min = 1.0, max = 3650.0,
            read = { it.retention.chat.retentionDays },
            apply = { c, v -> c.copy(retention = c.retention.copy(chat = c.retention.chat.copy(retentionDays = v as Long))) },
        )
        fields += ConfigField(
            key = "app.retention.chat.cron", group = "Retention", type = ConfigType.STRING,
            read = { it.retention.chat.cron },
            apply = { c, v -> c.copy(retention = c.retention.copy(chat = c.retention.chat.copy(cron = v as String))) },
            validate = { v -> requireValidCron(v as String) },
        )
        fields += ConfigField(
            key = "app.retention.audit.retention-days", group = "Retention", type = ConfigType.LONG, min = 1.0, max = 3650.0,
            read = { it.retention.audit.retentionDays },
            apply = { c, v -> c.copy(retention = c.retention.copy(audit = c.retention.audit.copy(retentionDays = v as Long))) },
        )
        fields += ConfigField(
            key = "app.retention.audit.cron", group = "Retention", type = ConfigType.STRING,
            read = { it.retention.audit.cron },
            apply = { c, v -> c.copy(retention = c.retention.copy(audit = c.retention.audit.copy(cron = v as String))) },
            validate = { v -> requireValidCron(v as String) },
        )

        // ---- Console ----
        fields += ConfigField(
            key = "app.console.max-page-size", group = "Console", type = ConfigType.INT, min = 10.0, max = 1000.0,
            read = { it.console.maxPageSize },
            apply = { c, v -> c.copy(console = c.console.copy(maxPageSize = v as Int)) },
        )

        return fields
    }

    private fun updateTier(
        tiers: Map<String, LlmProperties.Tier>,
        name: String,
        transform: (LlmProperties.Tier) -> LlmProperties.Tier,
    ): Map<String, LlmProperties.Tier> {
        val current = tiers[name] ?: return tiers
        return tiers.toMutableMap().apply { put(name, transform(current)) }
    }

    private companion object {
        private val STRING_LIST_TYPE = object : TypeReference<List<String>>() {}
        private val TIER_KEY_REGEX = Regex("""app\.llm\.tiers\.([^.]+)\.(model|temperature|max-tokens)""")

        // Placeholder tokens the critic template MUST contain; substituted at runtime in ReplyCritic.
        // Keep in sync with ReplyCritic's render() tokens.
        private val REQUIRED_CRITIC_PLACEHOLDERS = listOf("{brief}", "{language}")

        private fun requireCriticPlaceholders(template: String) {
            val missing = REQUIRED_CRITIC_PLACEHOLDERS.filterNot { template.contains(it) }
            if (missing.isNotEmpty()) {
                throw ConfigValidationException(
                    "Critic prompt is missing required placeholder(s): ${missing.joinToString(", ")}. " +
                        "The template must include ${REQUIRED_CRITIC_PLACEHOLDERS.joinToString(", ")}.",
                )
            }
        }

        private fun requireValidCron(expression: String) {
            if (!CronExpression.isValidExpression(expression)) {
                throw ConfigValidationException("Invalid cron expression: '$expression'")
            }
        }
    }
}
