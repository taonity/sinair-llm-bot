package org.taonity.sinairllmbot.bot.tools

import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage

enum class ToolCapability {
    REPOSITORY,
    REPOSITORY_WRITE,
    APPLICATION,
    CHAT_COMMAND,
    LOGS,
}

data class ToolExecutionContext(
    val roomTarget: String,
    val triggerMessageId: String,
    val botName: String,
    val completedStages: List<PipelineStage> = emptyList(),
    val configRevisionId: String? = null,
)

interface LlmToolContributor {
    val capability: ToolCapability

    fun definitions(context: ToolExecutionContext): List<Tool>

    fun supports(name: String): Boolean

    fun execute(context: ToolExecutionContext, name: String, argumentsJson: String): String
}
