package org.taonity.sinairllmbot.bot.metrics

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper

class OpenRouterUsageMetricsTest {
    @Test
    fun `provider usage distinguishes missing data zero spend and failed refreshes`() {
        val server = WireMockServer(options().dynamicPort())
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        server.start()
        try {
            val settings = mock(BotSettings::class.java)
            val properties = mock(LlmProperties::class.java)
            `when`(settings.llm()).thenReturn(properties)
            `when`(properties.baseUrl).thenReturn(server.baseUrl())
            `when`(properties.apiKey).thenReturn("test-key")
            val metrics = OpenRouterUsageMetrics(registry, settings, ObjectMapper(), true, 2)
            val daily = registry.get("bot.llm.spend.usd").tags("source", "openrouter", "period", "day").gauge()
            val fresh = registry.get("bot.llm.usage.timestamp.seconds").gauge()
            assertThat(daily.value()).isNaN()
            assertThat(fresh.value()).isZero()
            server.stubFor(get(urlEqualTo("/key")).willReturn(okJson("""{"data":{"usage":12,"usage_daily":0,"usage_weekly":2,"usage_monthly":5}}""")))
            metrics.refresh()
            assertThat(daily.value()).isZero()
            assertThat(fresh.value()).isPositive()
            assertThat(registry.get("bot.llm.spend.usd").tags("source", "byok", "period", "day").gauge().value()).isNaN()
            val timestamp = fresh.value()
            server.verify(getRequestedFor(urlEqualTo("/key")).withHeader("Authorization", equalTo("Bearer test-key")))

            server.stubFor(get(urlEqualTo("/key")).willReturn(okJson("""{"data":{"usage":13}}""")))
            metrics.refresh()
            assertThat(fresh.value()).isEqualTo(timestamp)
            assertThat(daily.value()).isZero()
            assertThat(registry.get("bot.llm.usage.poll.success").gauge().value()).isZero()

            server.stubFor(get(urlEqualTo("/key")).willReturn(aResponse().withStatus(401).withBody("Do not log this body")))
            metrics.refresh()
            assertThat(fresh.value()).isEqualTo(timestamp)
            assertThat(registry.get("bot.llm.usage.poll.success").gauge().value()).isZero()
        } finally {
            registry.close()
            server.stop()
        }
    }
}