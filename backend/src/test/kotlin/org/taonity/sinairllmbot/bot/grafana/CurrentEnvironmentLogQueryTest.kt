package org.taonity.sinairllmbot.bot.grafana

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import tools.jackson.module.kotlin.jacksonObjectMapper

class CurrentEnvironmentLogQueryTest {
    private val scope = CurrentEnvironmentLogQuery(
        containerPrefix = "sinair-llm-bot",
        allowedServices = listOf("backend", "frontend", "chat-collector"),
        objectMapper = jacksonObjectMapper(),
    )

    @Test
    fun `searches every related container in the current environment`() {
        assertThat(scope.build("connection refused", regex = false, requestedServices = emptyList()))
            .isEqualTo(
                "{container=~\"^sinair-llm-bot-(?:backend|chat-collector|frontend)-[0-9]+$\"} |= \"connection refused\"",
            )
    }

    @Test
    fun `limits a search to requested related services`() {
        assertThat(scope.build("ERROR|WARN", regex = true, requestedServices = listOf("backend")))
            .isEqualTo("{container=~\"^sinair-llm-bot-(?:backend)-[0-9]+$\"} |~ \"ERROR|WARN\"")
    }

    @Test
    fun `rejects containers outside the current environment`() {
        assertThatIllegalArgumentException()
            .isThrownBy { scope.build(null, regex = false, requestedServices = listOf("sinair-llm-bot-stage-backend")) }
            .withMessageContaining("Only current-environment services are allowed")
    }
}

class GrafanaMcpToolContributorTest {
    private val client = RecordingGrafanaLogClient()
    private val tracker = mock(PipelineContextTracker::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val properties = GrafanaMcpProperties(
        enabled = true,
        baseUrl = "http://grafana-mcp:8000",
        endpoint = "/mcp",
        requestTimeoutSeconds = 20,
        datasourceUid = "loki-prod",
        containerPrefix = "sinair-llm-bot",
        services = listOf("backend", "frontend"),
        maxResults = 50,
        maxResultChars = 20000,
    )
    private val contributor = GrafanaMcpToolContributor(client, properties, objectMapper, tracker)
    private val context = ToolExecutionContext("#room", "message-1", "segfault")

    @Test
    fun `translates structured search into a scoped MCP Loki query`() {
        contributor.execute(
            context,
            "search_current_environment_logs",
            """{"search":"ERROR","services":["backend"],"startRfc3339":"now-30m","limit":12}""",
        )

        assertThat(client.arguments).containsEntry("datasourceUid", "loki-prod")
        assertThat(client.arguments).containsEntry(
            "logql",
            "{container=~\"^sinair-llm-bot-(?:backend)-[0-9]+$\"} |= \"ERROR\"",
        )
        assertThat(client.arguments).containsEntry("startRfc3339", "now-30m")
        assertThat(client.arguments).containsEntry("limit", 12)
    }

    @Test
    fun `rejects another environment before calling MCP`() {
        val result = contributor.execute(
            context,
            "search_current_environment_logs",
            """{"services":["sinair-llm-bot-stage-backend"]}""",
        )

        assertThat(result).startsWith("ERROR: Only current-environment services are allowed")
        assertThat(client.arguments).isNull()
    }

    private class RecordingGrafanaLogClient : GrafanaLogClient {
        var arguments: Map<String, Any>? = null

        override fun queryLogs(arguments: Map<String, Any>): String {
            this.arguments = arguments
            return "ok"
        }
    }
}