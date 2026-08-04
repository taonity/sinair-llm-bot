package org.taonity.sinairllmbot.bot.grafana

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.tools.LlmToolContributor
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import tools.jackson.databind.ObjectMapper

@Service
@ConditionalOnProperty(prefix = "app.grafana.mcp", name = ["enabled"], havingValue = "true")
class GrafanaMcpToolContributor(
    private val client: GrafanaLogClient,
    private val properties: GrafanaMcpProperties,
    private val objectMapper: ObjectMapper,
    private val pipelineContextTracker: PipelineContextTracker,
) : LlmToolContributor {
    private val queryScope = CurrentEnvironmentLogQuery(
        properties.containerPrefix,
        properties.services,
        objectMapper,
    )

    override val capability = ToolCapability.LOGS

    override fun definitions(context: ToolExecutionContext): List<Tool> = listOf(
        Tool.function(
            name = TOOL_NAME,
            description = "Search Loki logs from this bot's current deployment only. " +
                "The query is always restricted to its related Docker containers.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "search" to mapOf(
                        "type" to "string",
                        "description" to "Text to find in log lines. Omit only when recent raw lines are needed.",
                        "maxLength" to MAX_SEARCH_LENGTH,
                    ),
                    "regex" to mapOf(
                        "type" to "boolean",
                        "description" to "Interpret search as an RE2 regular expression instead of literal text.",
                        "default" to false,
                    ),
                    "services" to mapOf(
                        "type" to "array",
                        "description" to "Related services to search. Omit to search all related containers.",
                        "items" to mapOf("type" to "string", "enum" to properties.services),
                        "uniqueItems" to true,
                    ),
                    "startRfc3339" to mapOf(
                        "type" to "string",
                        "description" to "Start time in RFC3339 or relative form such as now-1h. Defaults to now-1h.",
                    ),
                    "endRfc3339" to mapOf(
                        "type" to "string",
                        "description" to "End time in RFC3339 or relative form such as now. Defaults to now.",
                    ),
                    "limit" to mapOf(
                        "type" to "integer",
                        "description" to "Maximum log lines to return.",
                        "minimum" to 1,
                        "maximum" to properties.maxResults,
                        "default" to DEFAULT_RESULTS,
                    ),
                ),
                "additionalProperties" to false,
            ),
        ),
    )

    override fun supports(name: String): Boolean = name == TOOL_NAME

    override fun execute(context: ToolExecutionContext, name: String, argumentsJson: String): String {
        if (name != TOOL_NAME) return "ERROR: unsupported Grafana tool '$name'"
        val arguments = runCatching { objectMapper.readValue(argumentsJson, SearchLogArguments::class.java) }
            .getOrElse { return "ERROR: invalid log search arguments" }
        if (arguments.search != null && arguments.search.length > MAX_SEARCH_LENGTH) {
            return "ERROR: log search text is too long"
        }
        val logql = runCatching {
            queryScope.build(arguments.search, arguments.regex, arguments.services)
        }.getOrElse { return "ERROR: ${it.message}" }
        val mcpArguments = buildMap<String, Any> {
            put("datasourceUid", properties.datasourceUid)
            put("logql", logql)
            put("limit", arguments.limit.coerceIn(1, properties.maxResults))
            put("direction", "backward")
            put("queryType", "range")
            arguments.startRfc3339?.takeIf { it.isNotBlank() }?.let { put("startRfc3339", it) }
            arguments.endRfc3339?.takeIf { it.isNotBlank() }?.let { put("endRfc3339", it) }
        }
        pipelineContextTracker.recordSource("grafana://loki/current-environment")
        return client.queryLogs(mcpArguments)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SearchLogArguments(
        val search: String? = null,
        val regex: Boolean = false,
        val services: List<String> = emptyList(),
        val startRfc3339: String? = null,
        val endRfc3339: String? = null,
        val limit: Int = DEFAULT_RESULTS,
    )

    private companion object {
        const val TOOL_NAME = "search_current_environment_logs"
        const val DEFAULT_RESULTS = 20
        const val MAX_SEARCH_LENGTH = 500
    }
}