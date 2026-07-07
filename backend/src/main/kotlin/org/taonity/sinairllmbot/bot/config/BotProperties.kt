package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Behaviour configuration for the chat bot.
 *
 * The bot deliberately does NOT answer every message. A cheap heuristic gate filters the bulk
 * for free; only ambiguous activity escalates to an LLM "interest" check, and only then to a
 * reply. Cooldown/rate limits keep it from spamming.
 */
@ConfigurationProperties(prefix = "app.bot")
data class BotProperties(
    val enabled: Boolean,
    val rooms: List<String>,
    val persona: Persona,
    val decision: Decision,
    val context: Context,
    val typing: Typing,
) {
    data class Persona(
        val name: String,
        val language: String,
        val prompt: String,
        val creatorUserId: Int,
        val stopCommand: String,
        val startCommand: String,
        val sleepCommand: String,
        val wakeCommand: String,
        val sleepNickSuffix: String,
        val aliases: List<String> = emptyList(),
    )

    data class Decision(
        val debounceSeconds: Long,
        val cooldownSeconds: Long,
        val maxRepliesPerWindow: Int,
        val windowMinutes: Long,
        val spontaneousProbability: Double,
    )

    data class Context(
        val recentMessageCount: Int,
        val summaryRefreshEveryMessages: Int,
        val maxSummaryChars: Int,
        val summaryMaxTokens: Int,
        val maxMessageChars: Int,
    )

    data class Typing(
        val ttlSeconds: Long,
    )
}
