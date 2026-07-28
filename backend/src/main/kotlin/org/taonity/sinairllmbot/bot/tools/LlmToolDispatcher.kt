package org.taonity.sinairllmbot.bot.tools

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool

@Service
class LlmToolDispatcher(
    private val contributors: List<LlmToolContributor>,
) {
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
