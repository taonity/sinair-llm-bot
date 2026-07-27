package org.taonity.sinairllmbot.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.GithubProperties
import org.taonity.sinairllmbot.bot.config.GithubSettings
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionSettings
import org.taonity.sinairllmbot.console.config.ConsolePagingProperties
import org.taonity.sinairllmbot.config.repository.BotConfigOverrideRepository
import org.taonity.sinairllmbot.config.repository.BotConfigTierRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * The live effective-config provider. Holds an atomically swappable snapshot built by overlaying
 * the DB overrides on top of the yaml `@ConfigurationProperties` defaults. Bot services read the
 * snapshot per message, so a saved change takes effect immediately with no restart.
 *
 * The snapshot starts as defaults-only (no DB access during bean init) and is warmed once the app
 * is ready and rebuilt after every write. An invalid/stale override row can never brick startup:
 * it is logged and skipped, falling back to that field's default.
 *
 * Custom tiers (rows in `bot_config_tier`) are folded into a "baseline" on top of the yaml defaults
 * before per-field overrides are applied, so a custom tier behaves exactly like a built-in one: its
 * seeded model/temperature/max-tokens act as the tier's default and per-field edits overlay on top.
 */
@Component
class BotSettings(
    private val botDefaults: BotProperties,
    private val llmDefaults: LlmProperties,
    private val ingestionDefaults: IngestionProperties,
    private val githubDefaults: GithubProperties,
    private val retentionDefaults: RetentionProperties,
    private val consoleDefaults: ConsolePagingProperties,
    private val registry: ConfigRegistry,
    private val overrideRepository: BotConfigOverrideRepository,
    private val tierRepository: BotConfigTierRepository,
) : IngestionSettings, GithubSettings {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val yamlDefaults = EffectiveConfig(botDefaults, llmDefaults, ingestionDefaults, githubDefaults, retentionDefaults, consoleDefaults)

    /** yaml defaults + custom tiers at their seeded values (the "reset" target for every field). */
    private val baseline = AtomicReference(yamlDefaults)
    private val snapshot = AtomicReference(yamlDefaults)

    fun bot(): BotProperties = snapshot.get().bot

    fun llm(): LlmProperties = snapshot.get().llm

    override fun ingestion(): IngestionProperties = snapshot.get().ingestion

    override fun github(): GithubProperties = snapshot.get().github

    fun retention(): RetentionProperties = snapshot.get().retention

    fun console(): ConsolePagingProperties = snapshot.get().console

    /** The reset target: yaml defaults with custom tiers folded in at their seeded values. */
    fun defaults(): EffectiveConfig = baseline.get()

    fun effective(): EffectiveConfig = snapshot.get()

    /** Names of the built-in (yaml) tiers, which — unlike custom tiers — cannot be deleted. */
    fun yamlTierNames(): Set<String> = llmDefaults.tiers.keys

    @EventListener(ApplicationReadyEvent::class)
    fun warmUp() = reload()

    /** Rebuilds the baseline (defaults + custom tiers) and the effective snapshot (+ overrides). */
    fun reload() {
        var base = yamlDefaults
        for (tier in tierRepository.findAll()) {
            base = base.copy(
                llm = base.llm.copy(
                    tiers = base.llm.tiers + (tier.name to LlmProperties.Tier(
                        model = tier.model,
                        temperature = tier.temperature,
                        maxTokens = tier.maxTokens,
                    )),
                ),
            )
        }
        baseline.set(base)

        var effective = base
        for (row in overrideRepository.findAll()) {
            val field = registry.field(row.configKey)
            if (field == null) {
                LOGGER.warn { "Ignoring unknown config override key '${row.configKey}'" }
                continue
            }
            effective = try {
                field.apply(effective, registry.parseStored(field, row.valueJson, effective))
            } catch (e: Exception) {
                LOGGER.warn(e) { "Ignoring invalid config override '${row.configKey}'" }
                effective
            }
        }
        snapshot.set(effective)
    }
}
