package org.taonity.sinairllmbot.bot.tools

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool
import tools.jackson.databind.ObjectMapper

@Service
class LlmToolDispatcher(
    private val contributors: List<LlmToolContributor>,
    private val objectMapper: ObjectMapper,
) {
    fun open(context: ToolExecutionContext, capabilities: Set<ToolCapability>): ToolSession =
        ToolSession(context, capabilities)

    inner class ToolSession(
        private val context: ToolExecutionContext,
        private val capabilities: Set<ToolCapability>,
    ) {
        private val loaded = linkedMapOf<String, Tool>()
        val readOnlyTools = mutableSetOf("discover_tools")
        val cacheableTools = mutableSetOf<String>()

        fun definitions(): List<Tool> = listOf(Tool.function(
            "discover_tools",
            "Load tools for a capability only when needed. Read schemas before calling them. " +
                "APPLICATION: live room state and diagnostics; REPOSITORY: code and files; " +
                "LOGS: runtime logs; CHAT_COMMAND: requested chat actions; REPOSITORY_WRITE: authorized repository changes.",
            mapOf(
                "type" to "object",
                "properties" to mapOf("capability" to mapOf("type" to "string", "enum" to capabilities.map { it.name })),
                "required" to listOf("capability"),
            ),
        )) + loaded.values

        fun execute(name: String, arguments: String): String {
            if (name != "discover_tools") {
                if (name !in loaded) return "ERROR: tool is not enabled for this request; discover its capability first"
                return this@LlmToolDispatcher.execute(context, name, arguments)
            }
            val capability = runCatching {
                ToolCapability.valueOf(objectMapper.readTree(arguments).path("capability").asString(""))
            }.getOrNull() ?: return "ERROR: invalid capability"
            if (capability !in capabilities) return "ERROR: capability is not authorized for this request"
            val definitions = this@LlmToolDispatcher.definitions(context, setOf(capability))
            definitions.forEach { tool ->
                val toolName = requireNotNull(tool.function).name
                loaded[toolName] = tool
                if (capability !in setOf(ToolCapability.REPOSITORY_WRITE, ToolCapability.CHAT_COMMAND)) readOnlyTools += toolName
                if (capability == ToolCapability.REPOSITORY) cacheableTools += toolName
            }
            return objectMapper.writeValueAsString(definitions)
        }
    }

    fun definitions(
        context: ToolExecutionContext,
        capabilities: Set<ToolCapability>,
    ): List<Tool> {
        val tools = contributors
            .filter { it.capability in capabilities }
            .flatMap { it.definitions(context) }
        val duplicates = tools.mapNotNull { it.function?.name }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) { "Duplicate LLM tool names: $duplicates" }
        return tools
    }

    fun execute(context: ToolExecutionContext, name: String, argumentsJson: String): String {
        val contributor = contributors.singleOrNull { it.supports(name) }
            ?: return "ERROR: unknown or ambiguous tool '$name'"
        return contributor.execute(context, name, argumentsJson)
    }
}
