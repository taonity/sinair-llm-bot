package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the LLM provider (OpenAI-compatible chat completions, e.g. OpenRouter).
 *
 * Tiers let the same client target different models for different jobs without code changes:
 *  - `gate`  — ultra-cheap model used for the "should I respond?" classifier and summaries.
 *  - `cheap` — default reply model (good cost/quality, strong in Russian).
 *  - `smart` — higher-quality reply model for testing.
 *
 * `activeReplyTier` selects which tier generates replies, so you can A/B test smart vs cheap.
 */
@ConfigurationProperties(prefix = "app.llm")
data class LlmProperties(
    val baseUrl: String,
    val apiKey: String,
    val timeoutSeconds: Long,
    val title: String?,
    val activeReplyTier: String,
    val gateTier: String,
    val criticTier: String,
    val tiers: Map<String, Tier>,
    val retry: Retry,
    /** When true, reply generation offers OpenRouter's `openrouter:web_search` server tool so the model can ground answers in live results. */
    val replyWebSearch: Boolean = false,
    /**
     * Total attempts a JSON-expecting prompt (triage, critic) makes to obtain a deserializable JSON
     * response before giving up: on a parse failure the same prompt is re-issued. 1 disables retries.
     */
    val jsonRetryAttempts: Int,
    val critic: Critic,
    /** Global tool-loop settings for reply generation. When any tool is available (web search, repo
     * lookup, app context) the reply model runs in an agentic tool loop instead of a single-shot
     * generation, so it can decide for itself whether to reach for a tool while composing the answer. */
    val toolLoop: ToolLoop = ToolLoop(),
) {
    data class Retry(
        val maxAttempts: Int,
        val backoffMillis: Long,
    )

    data class Tier(
        val model: String,
        val temperature: Double,
        val maxTokens: Int,
    )

    data class Critic(
        val enabled: Boolean,
        val candidateCount: Int,
        val candidateTemperature: Double,
        val repairThreshold: Int,
        val prompt: String,
    )

    data class ToolLoop(
        /** Tier name to use for tool-loop replies. When blank, falls back to [activeReplyTier]. */
        val tier: String = "",
        /** Max tool-call rounds before the model must answer; caps latency and per-message cost. */
        val maxRounds: Int = 8,
    )

    fun tier(name: String): Tier =
        tiers[name] ?: error("LLM tier '$name' is not configured under app.llm.tiers")
}
