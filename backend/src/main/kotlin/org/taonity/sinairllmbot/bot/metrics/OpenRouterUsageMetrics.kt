package org.taonity.sinairllmbot.bot.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "info.app", name = ["environment"], havingValue = "prod")
class OpenRouterUsageMetrics(
    registry: MeterRegistry,
    private val settings: BotSettings,
    private val objectMapper: ObjectMapper,
    @Value("\${app.observability.openrouter.enabled}") private val enabled: Boolean,
    @Value("\${app.observability.openrouter.timeout-seconds}") timeoutSeconds: Long,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val PERIOD_FIELDS = mapOf("all_time" to "usage", "day" to "usage_daily", "week" to "usage_weekly", "month" to "usage_monthly")
    }

    @Volatile private var snapshot = UsageSnapshot(emptyMap(), 0.0)
    @Volatile private var pollSuccess = Double.NaN
    private val client = RestClient.builder()
        .baseUrl(settings.llm().baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
            setReadTimeout(Duration.ofSeconds(timeoutSeconds))
        }).build()

    init {
        for (source in listOf("openrouter", "byok")) {
            for (period in PERIOD_FIELDS.keys) {
                Gauge.builder("bot.llm.spend.usd", this) { it.snapshot.values["$source:$period"] ?: Double.NaN }
                    .description("Provider-reported key usage in USD; day, week and month follow UTC calendar boundaries")
                    .tags("source", source, "period", period).register(registry)
            }
        }
        Gauge.builder("bot.llm.usage.timestamp.seconds", this) { it.snapshot.timestamp }.register(registry)
        Gauge.builder("bot.llm.usage.poll.success", this) { it.pollSuccess }.register(registry)
    }

    @Async
    @Scheduled(fixedDelayString = "\${app.observability.openrouter.interval-ms}")
    fun refresh() {
        if (!enabled) return
        runCatching {
            val body = client.get().uri("/key")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${settings.llm().apiKey}")
                .retrieve().body(String::class.java) ?: error("Empty key usage response")
            val data = objectMapper.readTree(body).path("data")
            val values = buildMap {
                PERIOD_FIELDS.forEach { (period, field) ->
                    put("openrouter:$period", usage(data, field, required = true))
                    put("byok:$period", usage(data, "byok_$field", required = false))
                }
            }
            snapshot = UsageSnapshot(values, Instant.now().epochSecond.toDouble())
            pollSuccess = 1.0
        }.onFailure {
            pollSuccess = 0.0
            LOGGER.warn { "OpenRouter usage poll failed: ${it.javaClass.simpleName}" }
        }
    }

    private fun usage(data: JsonNode, field: String, required: Boolean): Double {
        val value = data.path(field)
        if (!required && (value.isMissingNode || value.isNull)) return Double.NaN
        require(value.isNumber) { "Missing numeric key usage" }
        return value.asDouble().also { require(it.isFinite() && it >= 0) { "Invalid key usage" } }
    }

    private data class UsageSnapshot(val values: Map<String, Double>, val timestamp: Double)
}