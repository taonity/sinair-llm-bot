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
    /** Rooms the bot is allowed to talk in (e.g. "#chat"). */
    val rooms: List<String>,
    val persona: Persona,
    val decision: Decision,
    val context: Context,
) {
    data class Persona(
        /** The bot's chat login/nick. Used to detect mentions and to ignore its own messages. */
        val name: String,
        val language: String,
        /** Free-form system prompt describing personality, tone and rules. */
        val prompt: String,
        /** Chat user_id of the bot's creator. 0 means not configured. */
        val creatorUserId: Int,
        /** Command that any room member can send to stop the bot (e.g. "!stop"). */
        val stopCommand: String,
        /** Command to re-enable the bot after it was stopped (e.g. "!start"). */
        val startCommand: String,
    )

    data class Decision(
        /** Quiet-period (seconds) to wait after the last message before evaluating a burst once. */
        val debounceSeconds: Long,
        /** Minimum seconds between two bot replies in the same room. */
        val cooldownSeconds: Long,
        /** Max replies allowed within [windowMinutes]. */
        val maxRepliesPerWindow: Int,
        val windowMinutes: Long,
        /** Probability (0..1) of a spontaneous chime-in when not addressed and gate says "maybe". */
        val spontaneousProbability: Double,
    )

    data class Context(
        /** How many recent messages to include in the prompt. */
        val recentMessageCount: Int,
        /** Refresh the rolling room summary after this many newly ingested messages. */
        val summaryRefreshEveryMessages: Int,
        /** Hard cap on summary length to bound tokens. */
        val maxSummaryChars: Int,
        /** Max characters of a single chat message kept in the prompt. */
        val maxMessageChars: Int,
    )
}
