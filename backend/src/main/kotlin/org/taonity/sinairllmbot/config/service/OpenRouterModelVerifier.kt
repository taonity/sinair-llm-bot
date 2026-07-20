package org.taonity.sinairllmbot.config.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.ConfigValidationException
import java.time.Duration

/**
 * Verifies that a model id exists in the provider's catalogue before a custom tier is created, so a
 * typo can never persist an unusable tier. Queries the OpenAI-compatible `GET {base-url}/models`
 * endpoint (OpenRouter by default) and checks the requested id against the returned list.
 *
 * A verified-not-present model is a user error (400); an unreachable provider is surfaced as a
 * distinct, retryable error rather than silently accepting the model.
 */
@Component
class OpenRouterModelVerifier(
    private val settings: BotSettings,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun verify(model: String) {
        val available = fetchModelIds()
        if (model !in available) {
            throw ConfigValidationException("Model '$model' was not found on OpenRouter")
        }
    }

    private fun fetchModelIds(): Set<String> {
        val llm = settings.llm()
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(15))
        }
        val client = RestClient.builder()
            .baseUrl(llm.baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()

        val response = try {
            client.get()
                .uri("/models")
                .headers { headers -> llm.apiKey.takeIf { it.isNotBlank() }?.let(headers::setBearerAuth) }
                .retrieve()
                .body(ModelsResponse::class.java)
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "Failed to fetch model catalogue from '${llm.baseUrl}/models'" }
            throw ConfigValidationException("Could not verify the model: the LLM provider is unreachable. Try again.")
        }

        return response?.data?.mapNotNull { it.id }?.toHashSet() ?: emptySet()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ModelsResponse(val data: List<ModelInfo> = emptyList())

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ModelInfo(val id: String?)
}
