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
    /** Optional OpenRouter attribution header. */
    val title: String?,
    /** Which tier generates replies: "cheap" or "smart". */
    val activeReplyTier: String,
    /** Tier used by the interest classifier and summarizer. */
    val gateTier: String,
    val tiers: Map<String, Tier>,
) {
    data class Tier(
        val model: String,
        val temperature: Double,
        val maxTokens: Int,
    )

    fun tier(name: String): Tier =
        tiers[name] ?: error("LLM tier '$name' is not configured under app.llm.tiers")
}
