package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

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
    val replyWebSearch: Boolean = false,
    val jsonRetryAttempts: Int,
    val critic: Critic,
    val toolLoop: ToolLoop = ToolLoop(),
) {
    data class Retry(
        val maxAttempts: Int,
        val backoffMillis: Long,
        val retryProviderErrors: Boolean,
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
        val prompt: Prompt,
    )

    data class ToolLoop(
        val tier: String = "",
        val maxRounds: Int = 8,
    )

    fun tier(name: String): Tier =
        tiers[name] ?: error("LLM tier '$name' is not configured under app.llm.tiers")
}
