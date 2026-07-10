package org.taonity.sinairllmbot.bot.client

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.taonity.sinairllmbot.bot.config.LlmProperties
import java.time.Duration

/**
 * Thin wrapper over an OpenAI-compatible chat-completions endpoint (OpenRouter by default).
 *
 * The model is chosen per call by tier name, so the same client serves the cheap classifier,
 * the summarizer and the (swappable) reply model.
 */
@Component
class LlmClient(
    private val llmProperties: LlmProperties,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val restClient: RestClient = buildRestClient()

    /**
     * Runs a completion against the given tier.
     *
     * @param forceJson when true, asks the provider to constrain output to a JSON object.
     * @param webSearch when true, offers OpenRouter's `openrouter:web_search` server tool so the
     *                  model can ground its answer in live results when it judges it useful (adds
     *                  latency and per-search cost only when the model actually searches).
     * @param maxTokensOverride when set, overrides the tier's default `maxTokens` for this call
     *                  (e.g. a longer output budget for summaries than for the cheap classifier).
     * @param temperatureOverride when set, overrides the tier's default `temperature` for this call
     *                  (e.g. raising it to draw diverse reply candidates for the critic layer).
     * @return the assistant text content, or null on any failure (caller decides how to degrade).
     */
    fun complete(
        tierName: String,
        messages: List<ChatMessage>,
        forceJson: Boolean = false,
        webSearch: Boolean = false,
        maxTokensOverride: Int? = null,
        temperatureOverride: Double? = null,
    ): LlmResult? {
        val tier = llmProperties.tier(tierName)
        val request = ChatCompletionRequest(
            model = tier.model,
            messages = messages,
            temperature = temperatureOverride ?: tier.temperature,
            maxTokens = maxTokensOverride ?: tier.maxTokens,
            responseFormat = if (forceJson) ResponseFormat.JSON_OBJECT else null,
            tools = if (webSearch) listOf(Tool.webSearch()) else null,
        )

        return try {
            val response = restClient.post()
                .uri("/chat/completions")
                .headers { headers ->
                    headers.setBearerAuth(llmProperties.apiKey)
                    headers.contentType = MediaType.APPLICATION_JSON
                    llmProperties.title?.let { headers.set("X-Title", it) }
                }
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse::class.java)

            val content = (response?.choices?.firstOrNull()?.message?.content as? String)?.trim()
            if (content.isNullOrBlank()) {
                LOGGER.warn { "LLM tier '$tierName' (${tier.model}) returned empty content" }
                return null
            }
            val citationUrls = response?.choices?.firstOrNull()?.message?.annotations
                ?.mapNotNull { it.urlCitation?.url }
                .orEmpty()
            val usage = response.usage
            LOGGER.info {
                "LLM tier=$tierName model=${tier.model} tokens=${usage?.totalTokens ?: "?"} " +
                    "(in=${usage?.promptTokens ?: "?"}, out=${usage?.completionTokens ?: "?"})"
            }
            if (webSearch) {
                if (citationUrls.isEmpty()) {
                    LOGGER.info { "LLM tier=$tierName: web_search offered but model returned no citations" }
                } else {
                    LOGGER.info {
                        "LLM tier=$tierName: web_search used, ${citationUrls.size} citation(s): " +
                            citationUrls.joinToString(", ")
                    }
                }
            }
            LlmResult(content = content, totalTokens = usage?.totalTokens ?: 0, citationUrls = citationUrls)
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "LLM call failed for tier '$tierName' (${tier.model})" }
            null
        }
    }

    private fun buildRestClient(): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(llmProperties.timeoutSeconds))
        }
        return RestClient.builder()
            .baseUrl(llmProperties.baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}

data class LlmResult(
    val content: String,
    val totalTokens: Int,
    val citationUrls: List<String> = emptyList(),
)
