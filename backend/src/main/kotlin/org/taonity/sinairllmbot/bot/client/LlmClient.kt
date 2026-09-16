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
import org.taonity.sinairllmbot.bot.pipeline.LlmCallStatus
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.bot.pipeline.ToolCallAttempt
import org.taonity.sinairllmbot.bot.pipeline.ToolCallEntry
import tools.jackson.databind.ObjectMapper
import java.time.Duration

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
        val transport = postChatCompletion(tierName, tier, request) ?: return null
        val rawResponse = transport.rawResponse

        val response = runCatching { objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java) }
            .getOrNull()
        val message = response?.choices?.firstOrNull()?.message
        val content = (message?.content as? String)?.trim()
        recordCall(
            tierName, tier, response, rawResponse, requestJson,
            if (webSearch) listOf("web_search") else emptyList(), emptyList(),
            attempt = transport.attempt,
            maxAttempts = transport.maxAttempts,
        )
        if (content.isNullOrBlank()) {
            LOGGER.warn { "LLM tier '$tierName' (${tier.model}) returned empty content" }
            return null
        }
        val citationUrls = message.annotations?.mapNotNull { it.urlCitation?.url }.orEmpty()
        if (webSearch) {
            LOGGER.info {
                val outcome = if (citationUrls.isEmpty()) "offered, no citations"
                else "used, ${citationUrls.size} citation(s)"
                "LLM tier=$tierName: web_search $outcome"
            }
        }
        return LlmResult(content = content, totalTokens = response?.usage?.totalTokens ?: 0, citationUrls = citationUrls)
    }

    fun completeWithTools(
        tierName: String,
        messages: List<ChatMessage>,
        tools: List<Tool>,
        maxRounds: Int,
        toolExecutor: (name: String, argumentsJson: String) -> String,
        readOnlyTools: Set<String> = emptySet(),
        cacheableTools: Set<String> = readOnlyTools,
        toolsProvider: (() -> List<Tool>)? = null,
    ): LlmResult? {
        val tier = llmProperties.tier(tierName)
        val budget = llmProperties.toolLoop
        val conversation = messages.toMutableList()
        var totalTokens = 0
        val totalIterations = maxRounds + 4
        val startedAt = System.nanoTime()
        val cachedResults = mutableMapOf<Pair<String, String>, String>()
        val citationUrls = linkedSetOf<String>()
        var finalizing = false
        var recovery = false
        var partial: String? = null
        var finalInstructionAdded = false
        var executedTools = 0
        var toolRounds = 0
        var boosted = false
        val evidenceExcerpts = mutableListOf<String>()
        fun result(content: String, incomplete: Boolean = false) = LlmResult(
            content, totalTokens, citationUrls.toList(), executedTools,
            evidenceExcerpts.takeLast(8).joinToString("\n") { it.take(budget.maxContextChars / 32) },
            incomplete,
        )

        for (round in 0 until totalIterations) {
            val iteration = round + 1
            val compacted = ToolConversationBudget.compact(conversation, budget.maxContextChars)
            if (ToolConversationBudget.size(conversation) > budget.maxContextChars) {
                LOGGER.warn { "Original prompt exceeds the configured context safety budget" }
                return partial?.let { result(it, incomplete = true) }
            }
            val expired = (System.nanoTime() - startedAt) / 1_000_000_000 >= budget.maxDurationSeconds
            finalizing = finalizing || toolRounds >= maxRounds || compacted || expired
            val offerTools = !finalizing
            LOGGER.info {
                "Agentic tier '$tierName' iteration $iteration/$totalIterations " +
                    "(${if (offerTools) "tools enabled" else "final answer"})"
            }
            if (!offerTools && !finalInstructionAdded) {
                finalInstructionAdded = true
                conversation += ChatMessage.user(
                    "Finish the user's request now using the evidence already collected. " +
                        "No more tools are available. Give the useful result first, distinguish evidence " +
                        "from inference, and state unresolved limitations briefly. An unsuccessful or " +
                        "bounded search is not proof of absence. Follow the final-answer format in your brief.",
                )
            }
            val request = ChatCompletionRequest(
                model = tier.model,
                messages = conversation.toList(),
                temperature = tier.temperature,
                maxTokens = when {
                    recovery -> maxOf(tier.maxTokens, budget.recoveryMaxTokens)
                    finalizing || boosted -> maxOf(tier.maxTokens, budget.finalMaxTokens)
                    else -> tier.maxTokens
                },
                tools = if (offerTools) toolsProvider?.invoke() ?: tools else null,
                reasoning = Reasoning(budget.reasoningEffort),
            )
            val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
            val transport = postChatCompletion(
                tierName = tierName,
                tier = tier,
                request = request,
                iteration = iteration,
                totalIterations = totalIterations,
            ) ?: run {
                if (recovery) return partial?.let { result(it, incomplete = true) }
                ToolConversationBudget.compact(conversation, budget.maxContextChars / 4)
                finalizing = true
                recovery = true
                continue
            }
            val rawResponse = transport.rawResponse
            val response = runCatching { objectMapper.readValue(rawResponse, ChatCompletionResponse::class.java) }
                .getOrNull()
            val choice = response?.choices?.firstOrNull()
            val message = choice?.message
            val toolCalls = message?.toolCalls.orEmpty()
            totalTokens += response?.usage?.totalTokens ?: 0
            citationUrls += message?.annotations?.mapNotNull { it.urlCitation?.url }.orEmpty()
            val truncated = choice?.finishReason?.lowercase() in TRUNCATION_FINISH_REASONS ||
                choice?.nativeFinishReason?.contains("MALFORMED", ignoreCase = true) == true

            // Execute each tool call the model asked for and capture a structured entry (name, args,
            // result) so the console can render the full tool-call exchange without raw-payload digging.
            val toolCallEntries = mutableListOf<ToolCallEntry>()
            if (offerTools && toolCalls.isNotEmpty() && !truncated) {
                // Echo the assistant's tool-call turn (without response-only annotations), then append
                // each tool result so the model can read them on the next round.
                conversation += message!!.copy(annotations = null)
                toolCalls.forEach { call ->
                    val name = call.function?.name.orEmpty()
                    val args = call.function?.arguments.orEmpty()
                    val cacheKey = name to runCatching { objectMapper.readTree(args).toString() }.getOrDefault(args)
                    val cached = cachedResults[cacheKey].takeIf { name in cacheableTools }
                    val execution = if (cached != null) ToolExecution(cached, emptyList(), 1)
                        else executeToolWithRetry(name, args, toolExecutor, name in readOnlyTools)
                    val result = ToolConversationBudget.boundResult(execution.result, budget.maxToolResultChars)
                    if (name != "discover_tools") evidenceExcerpts += "$name:\n$result"
                    if (name in cacheableTools && !result.startsWith("ERROR:")) cachedResults[cacheKey] = result
                    if (name !in readOnlyTools) cachedResults.clear()
                    if (name != "discover_tools") executedTools++
                    val isError = result.startsWith("ERROR:")
                    conversation += ChatMessage.tool(call.id.orEmpty(), result)
                    toolCallEntries += ToolCallEntry(
                        name = name,
                        arguments = args,
                        result = result,
                        error = isError,
                        attempts = execution.attempts,
                        maxAttempts = execution.maxAttempts,
                    )
                    if (isError) {
                        LOGGER.warn {
                            "Agentic tier '$tierName' iteration $iteration/$totalIterations: " +
                                "tool '$name' returned an error"
                        }
                    } else {
                        LOGGER.info {
                            "Agentic tier '$tierName' iteration $iteration/$totalIterations: " +
                                "tool '$name' executed -> ${result.length} chars"
                        }
                    }
                }
            }
            recordCall(
                tierName, tier, response, rawResponse, requestJson,
                toolCalls.mapNotNull { it.function?.name },
                toolCallEntries,
                attempt = transport.attempt,
                maxAttempts = transport.maxAttempts,
                iteration = iteration,
                totalIterations = totalIterations,
            )

            if (offerTools && toolCalls.isNotEmpty() && !truncated) {
                toolRounds++
                continue
            }

            val content = (message?.content as? String)?.trim()
            if (!content.isNullOrBlank() && !truncated) {
                return result(content)
            }
            if (!content.isNullOrBlank()) partial = content
            LOGGER.warn {
                "Agentic tier '$tierName' needs finalization (iteration=$iteration, finish=${choice?.finishReason})"
            }
            if (offerTools && truncated && !boosted) {
                boosted = true
                continue
            }
            if (recovery) break
            recovery = finalizing
            finalizing = true
        }
        return partial?.let { result(it, incomplete = true) }
    }

    private fun postChatCompletion(
        tierName: String,
        tier: LlmProperties.Tier,
        request: ChatCompletionRequest,
        iteration: Int? = null,
        totalIterations: Int? = null,
    ): LlmTransportResult? {
        val iterationLabel = iteration?.let { "iteration $it/${totalIterations ?: "?"}" }
        val callLabel = "tier=$tierName" + iterationLabel?.let { ", $it" }.orEmpty()
        val requestJson = runCatching { objectMapper.writeValueAsString(request) }.getOrDefault("")
        LOGGER.debug { "OpenRouter request ($callLabel): ${requestJson.length} chars" }
        return try {
            val retry = llmProperties.retry
            var attempt = 0
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
                attempt++
                try {
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
                } catch (exception: Exception) {
                    val responsePayload = failurePayload(exception)
                    val response = runCatching {
                        objectMapper.readValue(responsePayload, ChatCompletionResponse::class.java)
                    }.getOrNull()
                    recordCall(
                        tierName, tier, response, responsePayload, requestJson, emptyList(), emptyList(),
                        attempt = attempt,
                        maxAttempts = retry.maxAttempts,
                        status = LlmCallStatus.FAILED,
                        error = exception.message ?: exception.javaClass.simpleName,
                        iteration = iteration,
                        totalIterations = totalIterations,
                    )
                    throw exception
                }
            }
            LOGGER.debug { "OpenRouter response ($callLabel): ${rawResponse?.length ?: 0} chars" }
            LlmTransportResult(rawResponse.orEmpty(), attempt, retry.maxAttempts)
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "LLM call failed ($callLabel, model=${tier.model})" }
            null
        }
    }

    private fun failurePayload(exception: Exception): String = when (exception) {
        is EmbeddedProviderException -> exception.rawResponse
        is RestClientResponseException -> exception.responseBodyAsString.takeIf { it.isNotBlank() }
            ?: errorPayload(exception, exception.statusCode.value())
        else -> errorPayload(exception)
    }

    private fun errorPayload(exception: Exception, status: Int? = null): String = runCatching {
        objectMapper.writeValueAsString(
            mapOf(
                "error" to mapOf(
                    "type" to exception.javaClass.name,
                    "message" to (exception.message ?: exception.javaClass.simpleName),
                    "httpStatus" to status,
                ),
            ),
        )
    }.getOrDefault(exception.message ?: exception.javaClass.simpleName)

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
        readOnly: Boolean,
    ): ToolExecution {
        val retry = llmProperties.retry
        val attempts = mutableListOf<ToolCallAttempt>()
        val result = try {
            RetryExecutor.execute(
                name = "tool-$name",
                maxAttempts = if (readOnly) retry.maxAttempts else 1,
                backoffMillis = retry.backoffMillis,
                shouldRetryResult = ::isRetryableToolResult,
                onRetry = { nextAttempt, cause ->
                    LOGGER.warn {
                        "Tool '$name' failed ($cause); retrying attempt $nextAttempt/${retry.maxAttempts}"
                    }
                },
                onAttempt = { attempt, attemptResult, exception ->
                    val rendered = attemptResult ?: toolFailureResult(name, exception)
                    attempts += ToolCallAttempt(attempt, rendered, rendered.startsWith("ERROR:"))
                },
            ) {
                toolExecutor(name, argumentsJson)
            }
        } catch (exception: Exception) {
            attempts.lastOrNull()?.result ?: toolFailureResult(name, exception)
        }
        return ToolExecution(result, attempts, if (readOnly) retry.maxAttempts else 1)
    }

    private fun toolFailureResult(name: String, exception: Exception?): String =
        "ERROR: tool '$name' failed: ${exception?.message ?: exception?.javaClass?.simpleName ?: "unknown error"}"

    private fun isRetryableToolResult(result: String): Boolean {
        if (!result.startsWith("ERROR:")) return false
        val normalized = result.lowercase()
        val retryable = RETRYABLE_TOOL_ERROR_MARKERS.any(normalized::contains)
        if (retryable) {
            LOGGER.warn { "Tool returned a retryable error" }
        }
        return retryable
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

    private data class LlmTransportResult(
        val rawResponse: String,
        val attempt: Int,
        val maxAttempts: Int,
    )

    private data class ToolExecution(
        val result: String,
        val attempts: List<ToolCallAttempt>,
        val maxAttempts: Int,
    )

    private fun recordCall(
        tierName: String,
        tier: LlmProperties.Tier,
        response: ChatCompletionResponse?,
        rawResponse: String?,
        requestJson: String,
        toolNames: List<String>,
        toolCallEntries: List<ToolCallEntry>,
        attempt: Int = 1,
        maxAttempts: Int = 1,
        status: LlmCallStatus = LlmCallStatus.SUCCEEDED,
        error: String? = null,
        iteration: Int? = null,
        totalIterations: Int? = null,
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
                attempt = attempt,
                maxAttempts = maxAttempts,
                status = status,
                error = error,
                iteration = iteration,
                totalIterations = totalIterations,
            ),
        )
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
    val toolCallCount: Int = 0,
    val evidence: String = "",
    val incomplete: Boolean = false,
)
