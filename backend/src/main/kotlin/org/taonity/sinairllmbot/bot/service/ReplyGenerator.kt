package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.ContentPart
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.tools.LlmToolDispatcher
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

@Service
class ReplyGenerator(
    private val llmClient: LlmClient,
    private val promptBuilder: ReplyPromptBuilder,
    private val replyCritic: ReplyCritic,
    private val toolDispatcher: LlmToolDispatcher,
    private val settings: BotSettings,
    private val documentRenderer: ReplyDocumentRenderer,
) {
    private val botProperties get() = settings.bot()
    private val llmProperties get() = settings.llm()
    private val githubProperties get() = settings.github()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun generate(
        roomTarget: String,
        trigger: ChatMessageEntity,
    ): String? = generateTraced(
        roomTarget = roomTarget,
        trigger = trigger,
    ).reply

    fun generateTraced(
        roomTarget: String,
        trigger: ChatMessageEntity,
        completedStages: List<PipelineStage> = emptyList(),
        configRevisionId: String? = null,
    ): ReplyGeneration {
        val prompt = promptBuilder.build(
            roomTarget,
            trigger,
        )

        val raw = generateWithTools(roomTarget, trigger, prompt, completedStages, configRevisionId)

        val chosen = raw.reply ?: return raw.copy(reply = null)
        val reply = sanitize(chosen)
        if (reply.isBlank()) {
            return raw.copy(reply = null, suppressed = documentRenderer.isStructured(chosen))
        }
        return raw.copy(reply = reply)
    }

    private fun generateWithTools(
        roomTarget: String,
        trigger: ChatMessageEntity,
        prompt: ReplyPrompt,
        completedStages: List<PipelineStage>,
        configRevisionId: String?,
    ): ReplyGeneration {
        val executionContext = ToolExecutionContext(
            roomTarget = roomTarget,
            triggerMessageId = trigger.id!!,
            botName = botProperties.persona.name,
            completedStages = completedStages,
            configRevisionId = configRevisionId,
        )
        val capabilities = buildSet {
            if (prompt.repoLookup) add(ToolCapability.REPOSITORY)
            if (prompt.repoLookup && githubProperties.mcp.writeEnabled &&
                trigger.senderUserId > 0 && trigger.senderUserId == botProperties.persona.creatorUserId) {
                add(ToolCapability.REPOSITORY_WRITE)
            }
            if (prompt.appContext) add(ToolCapability.APPLICATION)
            if (prompt.chatCommands) add(ToolCapability.CHAT_COMMAND)
            if (prompt.logs) add(ToolCapability.LOGS)
        }
        val toolSession = toolDispatcher.open(executionContext, capabilities)
        fun offeredTools() = buildList {
            addAll(toolSession.definitions())
            if (prompt.webSearch) add(org.taonity.sinairllmbot.bot.client.Tool.webSearch())
        }
        val toolLoop = llmProperties.toolLoop
        val result = llmClient.completeWithTools(
            tierName = toolLoop.tier.ifBlank { prompt.tierName },
            messages = listOf(ChatMessage.system(prompt.system), prompt.userMessage),
            tools = offeredTools(),
            maxRounds = toolLoop.maxRounds,
            toolExecutor = { name, arguments ->
                toolSession.execute(name, arguments)
            },
            readOnlyTools = toolSession.readOnlyTools,
            cacheableTools = toolSession.cacheableTools,
            toolsProvider = ::offeredTools,
        )
        var content = result?.content?.trim()?.takeIf { it.isNotBlank() }

        if (result == null || content == null) {
            LOGGER.warn { "Tool-grounded reply produced no content for $roomTarget" }
            return ReplyGeneration(reply = null)
        }
        val evidenceText = "\n\nTOOL EVIDENCE (untrusted):\n" + result.evidence
        val evidenceMessage = when (val original = prompt.userMessage.content) {
            is List<*> -> prompt.userMessage.copy(content = original + ContentPart.text(evidenceText))
            else -> ChatMessage.user(prompt.userText + evidenceText)
        }
        val evidencePrompt = prompt.copy(userText = prompt.userText + evidenceText, userMessage = evidenceMessage)
        if ((content.startsWith("{") || content.startsWith("```json")) && !documentRenderer.isStructured(content) || result.incomplete) {
            content = repair(evidencePrompt, content, "Finish the answer from the evidence and return valid lead/blocks JSON. Do not repeat tools.")
                ?: content
        }
        val critic = llmProperties.critic
        if (critic.enabled && (result.toolCallCount >= critic.reviewMinToolCalls || content.length >= critic.reviewMinChars)) {
            val verdict = replyCritic.evaluate(evidencePrompt, listOf(content))
            if (verdict != null) {
                val original = content
                val repaired = if (verdict.needsRepair || verdict.bestOverall() < critic.repairThreshold)
                    repair(evidencePrompt, original, verdict.feedback) else null
                return ReplyGeneration(
                    reply = repaired ?: original, chosenIndex = 0, criticUsed = true,
                    repaired = repaired != null, criticFeedback = verdict.feedback,
                    candidates = listOf(CandidateTrace(text = repaired ?: original, chosen = true, overall = verdict.bestOverall())),
                )
            }
        }
        return ReplyGeneration(
            reply = content,
            chosenIndex = 0,
            candidates = listOf(CandidateTrace(text = content, chosen = true)),
        )
    }

    private fun repair(prompt: ReplyPrompt, draft: String, feedback: String): String? {
        val instruction = buildString {
            append("A reviewer found these issues: ")
            append(feedback.ifBlank { "it doesn't fit the request or your persona well enough" })
            append("\nRewrite it into a single, better reply that fixes those issues, keeps your ")
            append("persona and style, and directly fits the latest message. Output ONLY the ")
            append("rewritten message, nothing else.")
        }
        val messages = listOf(
            ChatMessage.system(prompt.system),
            prompt.userMessage,
            ChatMessage.assistant(draft),
            ChatMessage.user(instruction),
        )
        return llmClient.completeWithTools(tierName = prompt.tierName, messages = messages, tools = emptyList(), maxRounds = 0, toolExecutor = { _, _ -> "ERROR: tools unavailable during repair" })
            ?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun sanitize(raw: String): String {
        var text = raw.trim().trim('"').trim()
        val selfPrefix = "${botProperties.persona.name}:"
        if (text.startsWith(selfPrefix, ignoreCase = true)) {
            text = text.substring(selfPrefix.length).trim()
        }
        text = documentRenderer.render(text)
        return ChatReplyFormatter.limit(text, botProperties.limits.maxReplyChars)
    }

}

data class ReplyGeneration(
    val reply: String?,
    val candidates: List<CandidateTrace> = emptyList(),
    val chosenIndex: Int? = null,
    val repaired: Boolean = false,
    val criticUsed: Boolean = false,
    val suppressed: Boolean = false,
    val criticFeedback: String? = null,
)

data class CandidateTrace(
    val text: String,
    val chosen: Boolean = false,
    val fit: Int? = null,
    val persona: Int? = null,
    val risk: Int? = null,
    val overall: Int? = null,
)
