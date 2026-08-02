package org.taonity.sinairllmbot.bot.github

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.config.GithubSettings
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.tools.LlmToolContributor
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext

@Service
@ConditionalOnProperty(prefix = "app.github.mcp", name = ["enabled"], havingValue = "true")
class GithubMcpReadToolContributor(
    private val mcpClient: GithubMcpClient,
    private val legacyTools: GithubToolService,
    private val settings: GithubSettings,
    private val pipelineContextTracker: PipelineContextTracker,
) : LlmToolContributor {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val LEGACY_PREFIX = "legacy_"
        private val LEGACY_TOOL_NAMES = setOf("legacy_search_code", "legacy_get_file", "legacy_list_repos")
    }

    private val allowedTools get() = settings.github().mcp.readTools.toSet()
    override val capability = ToolCapability.REPOSITORY

    override fun definitions(context: ToolExecutionContext): List<Tool> = runCatching {
        mcpClient.definitions(allowedTools).also {
            check(it.isNotEmpty()) { "GitHub MCP returned none of the configured read tools" }
        }
    }.getOrElse {
        LOGGER.warn(it) { "GitHub MCP discovery failed; using legacy REST repository tools" }
        legacyTools.toolDefinitions().map { tool ->
            val function = requireNotNull(tool.function)
            Tool.function(
                name = "$LEGACY_PREFIX${function.name}",
                description = function.description,
                parameters = function.parameters,
            )
        }
    }

    override fun supports(name: String): Boolean =
        name in allowedTools || name in LEGACY_TOOL_NAMES

    override fun execute(context: ToolExecutionContext, name: String, argumentsJson: String): String {
        pipelineContextTracker.recordSource("github://mcp/$name")
        return if (name in LEGACY_TOOL_NAMES) {
            legacyTools.execute(context, name.removePrefix(LEGACY_PREFIX), argumentsJson)
        } else {
            mcpClient.execute(name, argumentsJson, allowedTools)
        }
    }

}

@Service
@ConditionalOnProperty(
    prefix = "app.github.mcp",
    name = ["enabled", "write-enabled"],
    havingValue = "true",
)
class GithubMcpWriteToolContributor(
    private val mcpClient: GithubMcpClient,
    private val settings: GithubSettings,
    private val pipelineContextTracker: PipelineContextTracker,
) : LlmToolContributor {
    private val allowedTools get() = settings.github().mcp.writeTools.toSet()

    override val capability = ToolCapability.REPOSITORY_WRITE

    override fun definitions(context: ToolExecutionContext): List<Tool> = mcpClient.definitions(allowedTools)

    override fun supports(name: String): Boolean = name in allowedTools

    override fun execute(context: ToolExecutionContext, name: String, argumentsJson: String): String {
        pipelineContextTracker.recordSource("github://mcp-write/$name")
        return mcpClient.execute(name, argumentsJson, allowedTools)
    }
}