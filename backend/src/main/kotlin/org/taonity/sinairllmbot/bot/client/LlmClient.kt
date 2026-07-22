package org.taonity.sinairllmbot.bot.client

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.pipeline.LlmCallUsage
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Thin wrapper over an OpenAI-compatible chat-completions endpoint (OpenRouter by default).
 *
 * The model is chosen per call by tier name, so the same client serves the cheap classifier,
 * the summarizer and the (swappable) reply model.
 */
@Component
class LlmClient(
    private val settings: BotSettings,
    private val objectMapper: ObjectMapper,
    private val pipelineLlmUsageTracker: PipelineLlmUsageTracker,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val llmProperties get() = settings.llm()
    private val restClient: RestClient = buildRestClient()
    private val prettyWriter = objectMapper.writerWithDefaultPrettyPrinter()

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
        // Serialized once so the persisted trace can show the exact request body sent to the provider.
        val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
        val rawResponse = postChatCompletion(tierName, tier, request) ?: return null

        val response = runCatching { objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java) }
            .getOrNull()
        val message = response?.choices?.firstOrNull()?.message
        val content = (message?.content as? String)?.trim()
        if (content.isNullOrBlank()) {
            LOGGER.warn { "LLM tier '$tierName' (${tier.model}) returned empty content" }
            return null
        }
        val citationUrls = message.annotations?.mapNotNull { it.urlCitation?.url }.orEmpty()
        recordCall(tierName, tier, response, rawResponse, requestJson, if (webSearch) listOf("web_search") else emptyList())
        if (webSearch) {
            LOGGER.info {
                val outcome = if (citationUrls.isEmpty()) "offered, no citations"
                else "used, ${citationUrls.size} citation(s)"
                "LLM tier=$tierName: web_search $outcome"
            }
        }
        return LlmResult(content = content, totalTokens = response?.usage?.totalTokens ?: 0, citationUrls = citationUrls)
    }

    /**
     * Agentic completion: offers the given client-side [tools] and runs a bounded tool-call loop. On
     * each round the model may ask to call one or more tools; [toolExecutor] runs them (read-only)
     * and their results are fed back until the model produces a final text answer or [maxRounds] tool
     * rounds are exhausted (after which one final, tool-free call forces an answer). Every provider
     * call is recorded to the pipeline usage tracker. Returns the final assistant text, or null.
     */
    fun completeWithTools(
        tierName: String,
        messages: List<ChatMessage>,
        tools: List<Tool>,
        maxRounds: Int,
        toolExecutor: (name: String, argumentsJson: String) -> String,
    ): LlmResult? {
        val tier = llmProperties.tier(tierName)
        val conversation = messages.toMutableList()
        var totalTokens = 0

        for (round in 0..maxRounds) {
            val offerTools = round < maxRounds
            val request = ChatCompletionRequest(
                model = tier.model,
                messages = conversation.toList(),
                temperature = tier.temperature,
                maxTokens = tier.maxTokens,
                tools = if (offerTools) tools else null,
            )
            val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
            val rawResponse = postChatCompletion(tierName, tier, request) ?: return null
            val response = runCatching { objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java) }
                .getOrNull()
            val message = response?.choices?.firstOrNull()?.message
            val toolCalls = message?.toolCalls.orEmpty()
            totalTokens += response?.usage?.totalTokens ?: 0
            recordCall(tierName, tier, response, rawResponse, requestJson, toolCalls.mapNotNull { it.function?.name })

            if (offerTools && toolCalls.isNotEmpty()) {
                // Echo the assistant's tool-call turn (without response-only annotations), then append
                // each tool result so the model can read them on the next round.
                conversation += ChatMessage(role = "assistant", content = message?.content, toolCalls = message?.toolCalls)
                toolCalls.forEach { call ->
                    val name = call.function?.name.orEmpty()
                    val result = runCatching { toolExecutor(name, call.function?.arguments.orEmpty()) }
                        .getOrElse { "ERROR: tool '$name' failed: ${it.message}" }
                    conversation += ChatMessage.tool(call.id.orEmpty(), result)
                    LOGGER.info { "Repo tool '$name' executed -> ${result.length} chars" }
                }
                continue
            }

            val content = (message?.content as? String)?.trim()
            if (!content.isNullOrBlank()) {
                val citationUrls = message?.annotations?.mapNotNull { it.urlCitation?.url }.orEmpty()
                return LlmResult(content = content, totalTokens = totalTokens, citationUrls = citationUrls)
            }
            LOGGER.warn { "Agentic tier '$tierName' returned no content on round $round" }
            return null
        }
        return null
    }

    /** POSTs a chat-completion request and returns the raw JSON body, or null on any transport error. */
    private fun postChatCompletion(
        tierName: String,
        tier: LlmProperties.Tier,
        request: ChatCompletionRequest,
    ): String? {
        LOGGER.debug { "OpenRouter request (tier=$tierName):\n${prettyJson(request)}" }
        return try {
            val rawResponse = restClient.post()
                .uri("/chat/completions")
                .headers { headers ->
                    headers.setBearerAuth(llmProperties.apiKey)
                    headers.contentType = MediaType.APPLICATION_JSON
                    llmProperties.title?.let { headers.set("X-Title", it) }
                }
                .body(request)
                .retrieve()
                .body(String::class.java)
            LOGGER.debug { "OpenRouter response (tier=$tierName):\n${prettyJson(rawResponse)}" }
            rawResponse
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "LLM call failed for tier '$tierName' (${tier.model})" }
            null
        }
    }

    /** Logs token usage and records the call (tokens, model, tool set, payloads) on the trace. */
    private fun recordCall(
        tierName: String,
        tier: LlmProperties.Tier,
        response: ChatCompletionResponse?,
        rawResponse: String?,
        requestJson: String,
        toolNames: List<String>,
    ) {
        val usage = response?.usage
        LOGGER.info {
            "LLM tier=$tierName model=${tier.model} tokens=${usage?.totalTokens ?: "?"} " +
                "(in=${usage?.promptTokens ?: "?"}, out=${usage?.completionTokens ?: "?"})"
        }
        pipelineLlmUsageTracker.record(
            LlmCallUsage(
                tier = tierName,
                model = tier.model,
                tokens = usage?.totalTokens ?: 0,
                tools = toolNames,
                requestPayload = requestJson,
                responsePayload = rawResponse.orEmpty(),
            ),
        )
    }

    /** Serializes a DTO to pretty JSON for debug logging, degrading gracefully on any failure. */
    private fun prettyJson(value: Any?): String = try {
        prettyWriter.writeValueAsString(value)
    } catch (exception: Exception) {
        "<unserializable: ${exception.message}>"
    }

    /** Re-indents an already-serialized JSON string for readable debug logging. */
    private fun prettyJson(json: String?): String = try {
        if (json.isNullOrBlank()) "<empty>" else prettyWriter.writeValueAsString(objectMapper.readTree(json))
    } catch (exception: Exception) {
        json ?: "<null>"
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
