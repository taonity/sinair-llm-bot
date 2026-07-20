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
    /** When true, reply generation offers OpenRouter's `openrouter:web_search` server tool so the model can ground answers in live results. */
    val replyWebSearch: Boolean = false,
    val critic: Critic,
) {
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

    fun tier(name: String): Tier =
        tiers[name] ?: error("LLM tier '$name' is not configured under app.llm.tiers")
}
