package org.taonity.sinairllmbot.bot.grafana

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

fun interface GrafanaLogClient {
    fun queryLogs(arguments: Map<String, Any>): String
}

@Component
@ConditionalOnProperty(prefix = "app.grafana.mcp", name = ["enabled"], havingValue = "true")
class GrafanaMcpClient(
    private val properties: GrafanaMcpProperties,
    private val objectMapper: ObjectMapper,
) : GrafanaLogClient {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val QUERY_TOOL = "query_loki_logs"
    }

    private val lock = Any()

    @Volatile
    private var client: McpSyncClient? = null

    override fun queryLogs(arguments: Map<String, Any>): String = runCatching {
        val connected = connect()
        val request = McpSchema.CallToolRequest.builder(QUERY_TOOL).arguments(arguments).build()
        render(connected.callTool(request))
    }.getOrElse {
        LOGGER.warn { "Grafana MCP log query failed: ${it.javaClass.simpleName}" }
        "ERROR: Grafana log query failed: ${it.javaClass.simpleName}"
    }

    private fun connect(): McpSyncClient {
        client?.let { return it }
        return synchronized(lock) {
            client?.let { return@synchronized it }
            val transport = HttpClientStreamableHttpTransport.builder(properties.baseUrl)
                .endpoint(properties.endpoint)
                .connectTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds))
                .build()
            val connected = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("sinair-llm-bot", "1.0").build())
                .requestTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds))
                .enableCallToolSchemaCaching(false)
                .build()
            try {
                connected.initialize()
                check(connected.listTools().tools().any { it.name() == QUERY_TOOL }) {
                    "Grafana MCP server does not expose $QUERY_TOOL"
                }
                LOGGER.info { "Connected to Grafana MCP server for current-environment log queries" }
                connected.also { client = it }
            } catch (exception: Exception) {
                connected.closeGracefully()
                throw exception
            }
        }
    }

    private fun render(result: McpSchema.CallToolResult): String {
        val text = result.content().joinToString("\n") { content ->
            when (content) {
                is McpSchema.TextContent -> content.text()
                else -> content.toString()
            }
        }.ifBlank {
            result.structuredContent()?.let(objectMapper::writeValueAsString).orEmpty()
        }
        val limited = text.take(properties.maxResultChars)
        val suffix = if (text.length > limited.length) "\n... [truncated]" else ""
        return if (result.isError() == true) "ERROR: $limited$suffix" else "$limited$suffix"
    }

    @PreDestroy
    fun close() {
        synchronized(lock) {
            client?.closeGracefully()
            client = null
        }
    }
}