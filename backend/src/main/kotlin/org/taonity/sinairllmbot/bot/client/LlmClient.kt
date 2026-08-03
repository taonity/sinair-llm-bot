package org.taonity.sinairllmbot.bot.client

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.pipeline.LlmCallUsage
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.bot.pipeline.ToolCallEntry
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
        // Finish reasons that mean the model's turn was cut off (usually max-tokens too small): a
        // plain `length` cap or Gemini's MALFORMED_FUNCTION_CALL (tool JSON truncated). We retry the
        // agentic round on these instead of giving up.
        private val TRUNCATION_FINISH_REASONS = setOf("length", "malformed_function_call")
        private val RETRYABLE_TOOL_ERROR_MARKERS = setOf(
            "failed",
            "timeout",
            "timed out",
            "unavailable",
            "connection",
            "rate limit",
            "too many requests",
        )
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
        recordCall(tierName, tier, response, rawResponse, requestJson, if (webSearch) listOf("web_search") else emptyList(), emptyList())
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
        val totalIterations = maxRounds + 1

        for (round in 0..maxRounds) {
            val iteration = round + 1
            val offerTools = round < maxRounds
            LOGGER.info {
                "Agentic tier '$tierName' iteration $iteration/$totalIterations " +
                    "(${if (offerTools) "tools enabled" else "final answer"})"
            }
            if (!offerTools) {
                conversation += ChatMessage.user(
                    "The investigation has reached its tool-call limit. Now wrap up " +
                        "the answer for the user in plain text suitable for chat. Be concise: say " +
                        "which tools, application records, repositories, files or search terms you " +
                        "checked; what you found; distinguish current state from historical snapshots; " +
                        "and, when relevant, what was close or inconclusive. Never claim that a file, " +
                        "record, feature or behavior does not exist merely because you did not find it. Say " +
                        "that you did not find enough evidence, or that the search was inconclusive, " +
                        "and state where you looked.",
                )
            }
            val request = ChatCompletionRequest(
                model = tier.model,
                messages = conversation.toList(),
                temperature = tier.temperature,
                maxTokens = tier.maxTokens,
                tools = if (offerTools) tools else null,
            )
            val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
            val rawResponse = postChatCompletion(
                tierName = tierName,
                tier = tier,
                request = request,
                iterationLabel = "iteration $iteration/$totalIterations",
            ) ?: return null
            val response = runCatching { objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java) }
                .getOrNull()
            val choice = response?.choices?.firstOrNull()
            val message = choice?.message
            val toolCalls = message?.toolCalls.orEmpty()
            totalTokens += response?.usage?.totalTokens ?: 0

            // Execute each tool call the model asked for and capture a structured entry (name, args,
            // result) so the console can render the full tool-call exchange without raw-payload digging.
            val toolCallEntries = mutableListOf<ToolCallEntry>()
            if (offerTools && toolCalls.isNotEmpty()) {
                // Echo the assistant's tool-call turn (without response-only annotations), then append
                // each tool result so the model can read them on the next round.
                conversation += ChatMessage(role = "assistant", content = message?.content, toolCalls = message?.toolCalls)
                toolCalls.forEach { call ->
                    val name = call.function?.name.orEmpty()
                    val args = call.function?.arguments.orEmpty()
                    val (result, isError) = runCatching { executeToolWithRetry(name, args, toolExecutor) }
                        .map { it to it.startsWith("ERROR:") }
                        .getOrElse { "ERROR: tool '$name' failed: ${it.message}" to true }
                    conversation += ChatMessage.tool(call.id.orEmpty(), result)
                    toolCallEntries += ToolCallEntry(name = name, arguments = args, result = result, error = isError)
                    LOGGER.info {
                        "Agentic tier '$tierName' iteration $iteration/$totalIterations: " +
                            "tool '$name' executed -> ${result.length} chars"
                    }
                }
            }
            recordCall(
                tierName, tier, response, rawResponse, requestJson,
                toolCalls.mapNotNull { it.function?.name },
                toolCallEntries,
            )

            if (offerTools && toolCalls.isNotEmpty()) {
                continue
            }

            val content = (message?.content as? String)?.trim()
            if (!content.isNullOrBlank()) {
                val citationUrls = message?.annotations?.mapNotNull { it.urlCitation?.url }.orEmpty()
                return LlmResult(content = content, totalTokens = totalTokens, citationUrls = citationUrls)
            }

            // No tool call and no text. A truncated/malformed tool call lands here: e.g. Gemini's
            // MALFORMED_FUNCTION_CALL (tool JSON cut off) or a `length` cap hit mid-output — usually
            // the tier's max-tokens is too small (thinking models spend it on hidden reasoning).
            // Retry the same round while tool rounds remain; the final tool-free round forces text.
            val finishReason = choice?.finishReason
            val nativeFinishReason = choice?.nativeFinishReason
            val truncated = finishReason?.lowercase() in TRUNCATION_FINISH_REASONS ||
                nativeFinishReason?.contains("MALFORMED", ignoreCase = true) == true
            LOGGER.warn {
                "Agentic tier '$tierName' produced no usable output on iteration " +
                    "$iteration/$totalIterations " +
                    "(finish=$finishReason native=$nativeFinishReason)"
            }
            if (offerTools && truncated) continue
            return null
        }
        return null
    }

    /** POSTs a chat-completion request and retries transient provider or transport failures. */
    private fun postChatCompletion(
        tierName: String,
        tier: LlmProperties.Tier,
        request: ChatCompletionRequest,
        iterationLabel: String? = null,
    ): String? {
        val callLabel = "tier=$tierName" + iterationLabel?.let { ", $it" }.orEmpty()
        LOGGER.debug { "OpenRouter request ($callLabel):\n${prettyJson(request)}" }
        return try {
            val retry = llmProperties.retry
            val rawResponse = RetryExecutor.execute(
                name = "llm-$tierName",
                maxAttempts = retry.maxAttempts,
                backoffMillis = retry.backoffMillis,
                shouldRetryException = { exception -> isRetryableLlmFailure(exception, retry.retryProviderErrors) },
                onRetry = { nextAttempt, cause ->
                    LOGGER.warn {
                        "LLM call failed ($callLabel, model=${tier.model}): $cause; " +
                            "retrying attempt $nextAttempt/${retry.maxAttempts}"
                    }
                },
            ) {
                restClient.post()
                    .uri("/chat/completions")
                    .headers { headers ->
                        headers.setBearerAuth(llmProperties.apiKey)
                        headers.contentType = MediaType.APPLICATION_JSON
                        llmProperties.title?.let { headers.set("X-Title", it) }
                    }
                    .body(request)
                    .retrieve()
                    .body(String::class.java)
                    .also { rawResponse ->
                        detectProviderError(rawResponse)?.let { error ->
                            throw EmbeddedProviderException(error, rawResponse.orEmpty())
                        }
                    }
            }
            LOGGER.debug { "OpenRouter response ($callLabel):\n${prettyJson(rawResponse)}" }
            rawResponse
        } catch (exception: EmbeddedProviderException) {
            val response = runCatching {
                objectMapper.readValue(exception.rawResponse, ChatCompletionResponse::class.java)
            }.getOrNull()
            val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
            recordCall(tierName, tier, response, exception.rawResponse, requestJson, emptyList(), emptyList())
            LOGGER.warn(exception) { "LLM call failed ($callLabel, model=${tier.model})" }
            null
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "LLM call failed ($callLabel, model=${tier.model})" }
            null
        }
    }

    private fun detectProviderError(rawResponse: String?): ChatCompletionResponse.ProviderError? {
        if (rawResponse.isNullOrBlank()) return null
        val response = runCatching {
            objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java)
        }.getOrNull() ?: return null
        return response.error ?: response.choices.firstNotNullOfOrNull { it.error }
    }

    private fun executeToolWithRetry(
        name: String,
        argumentsJson: String,
        toolExecutor: (name: String, argumentsJson: String) -> String,
    ): String {
        val retry = llmProperties.retry
        return RetryExecutor.execute(
            name = "tool-$name",
            maxAttempts = retry.maxAttempts,
            backoffMillis = retry.backoffMillis,
            shouldRetryResult = ::isRetryableToolResult,
            onRetry = { nextAttempt, cause ->
                LOGGER.warn {
                    "Tool '$name' failed ($cause); retrying attempt $nextAttempt/${retry.maxAttempts}"
                }
            },
        ) {
            toolExecutor(name, argumentsJson)
        }
    }

    private fun isRetryableToolResult(result: String): Boolean {
        if (!result.startsWith("ERROR:")) return false
        val normalized = result.lowercase()
        return RETRYABLE_TOOL_ERROR_MARKERS.any(normalized::contains)
    }

    private fun isRetryableLlmFailure(exception: Exception, retryProviderErrors: Boolean): Boolean = when (exception) {
        is ResourceAccessException -> true
        is RestClientResponseException -> exception.statusCode.value() == 429 || exception.statusCode.is5xxServerError
        is EmbeddedProviderException -> exception.error.shouldRetry(retryProviderErrors)
        else -> false
    }

    private class EmbeddedProviderException(
        val error: ChatCompletionResponse.ProviderError,
        val rawResponse: String,
    ) : RuntimeException(
        "provider response error code=${error.code ?: "unknown"}: ${error.message ?: "no message"}",
    )

    /** Logs token usage and records the call (tokens, model, tool set, payloads) on the trace. */
    private fun recordCall(
        tierName: String,
        tier: LlmProperties.Tier,
        response: ChatCompletionResponse?,
        rawResponse: String?,
        requestJson: String,
        toolNames: List<String>,
        toolCallEntries: List<ToolCallEntry>,
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
                toolCalls = toolCallEntries,
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
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
