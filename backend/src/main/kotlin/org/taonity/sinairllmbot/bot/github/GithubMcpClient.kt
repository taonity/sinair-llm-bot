package org.taonity.sinairllmbot.bot.github

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.config.GithubSettings
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpRequest
import java.time.Duration

@Component
@ConditionalOnProperty(prefix = "app.github.mcp", name = ["enabled"], havingValue = "true")
class GithubMcpClient(
    private val settings: GithubSettings,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val properties get() = settings.github()
    private val lock = Any()

    @Volatile
    private var client: McpSyncClient? = null

    @Volatile
    private var discoveredTools: List<McpSchema.Tool>? = null

    fun definitions(allowedTools: Set<String>): List<Tool> = withClient { connected ->
        val tools = discoveredTools ?: synchronized(lock) {
            discoveredTools ?: connected.listTools().tools().also { discoveredTools = it }
        }
        tools
            .filter { it.name() in allowedTools }
            .map { tool ->
                Tool.function(
                    name = tool.name(),
                    description = tool.description().orEmpty(),
                    parameters = tool.inputSchema(),
                )
            }
    }

    fun execute(name: String, argumentsJson: String, allowedTools: Set<String>): String {
        if (name !in allowedTools) return "ERROR: GitHub MCP tool '$name' is not allowed."
        val arguments = runCatching {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(argumentsJson, Map::class.java) as Map<String, Any>
        }.getOrElse { return "ERROR: invalid arguments for GitHub MCP tool '$name': ${it.message}" }

        return runCatching {
            withClient { connected ->
                val request = McpSchema.CallToolRequest.builder(name).arguments(arguments).build()
                render(connected.callTool(request))
            }
        }.getOrElse {
            LOGGER.warn(it) { "GitHub MCP tool '$name' failed" }
            "ERROR: GitHub MCP tool '$name' failed: ${it.message}"
        }
    }

    private fun <T> withClient(action: (McpSyncClient) -> T): T = action(connect())

    private fun connect(): McpSyncClient {
        client?.let { return it }
        return synchronized(lock) {
            client?.let { return@synchronized it }
            val github = properties
            val mcp = github.mcp
            val offeredTools = buildList {
                addAll(mcp.readTools)
                if (mcp.writeEnabled) addAll(mcp.writeTools)
            }
            val requestBuilder = HttpRequest.newBuilder()
                .header("X-MCP-Tools", offeredTools.joinToString(","))
                .header("X-MCP-Readonly", (!mcp.writeEnabled).toString())
                .header("X-MCP-Lockdown", "true")
            github.token?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("Authorization", "Bearer $it") }

            val transport = HttpClientStreamableHttpTransport.builder(mcp.baseUrl)
                .endpoint(mcp.endpoint)
                .requestBuilder(requestBuilder)
                .connectTimeout(Duration.ofSeconds(mcp.requestTimeoutSeconds))
                .build()
            val connected = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("sinair-llm-bot", "1.0").build())
                .requestTimeout(Duration.ofSeconds(mcp.requestTimeoutSeconds))
                .enableCallToolSchemaCaching(false)
                .build()
            try {
                connected.initialize()
                LOGGER.info { "Connected to GitHub MCP server with ${offeredTools.size} allowlisted tools" }
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
        val limited = text.take(properties.repoLookup.maxFileChars)
        val suffix = if (text.length > limited.length) "\n... [truncated]" else ""
        return if (result.isError() == true) "ERROR: $limited$suffix" else "$limited$suffix"
    }

    @PreDestroy
    fun close() {
        synchronized(lock) {
            client?.closeGracefully()
            client = null
            discoveredTools = null
        }
    }
}