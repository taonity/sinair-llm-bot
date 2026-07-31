package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.tools.LlmToolDispatcher
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

/**
 * Final stage: produces the actual chat reply.
 *
 * When any tool is available (web search, repo lookup, app context) the reply model runs in an
 * agentic tool loop with global round settings from [LlmProperties.ToolLoop], so it can decide
 * for itself whether to reach for a tool while composing the answer. When no tool is available
 * and the critic layer is enabled it draws several candidates, has a cheap judge rate them against
 * the same brief, keeps the best and repairs it once if it scores too low. Otherwise it falls back
 * to a single direct generation. Prompt assembly (persona + context + any grounded link/image
 * content, plus vision-tier routing) lives in [ReplyPromptBuilder].
 */
@Service
class ReplyGenerator(
    private val llmClient: LlmClient,
    private val promptBuilder: ReplyPromptBuilder,
    private val candidateGenerator: CandidateGenerator,
    private val replyCritic: ReplyCritic,
    private val toolDispatcher: LlmToolDispatcher,
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()
    private val llmProperties get() = settings.llm()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    /**
     * @param roomTarget the room the trigger message belongs to.
     * @param trigger the message the bot is replying to.
     */
    fun generate(
        roomTarget: String,
        trigger: ChatMessageEntity,
    ): String? = generateTraced(
        roomTarget = roomTarget,
        trigger = trigger,
    ).reply

    /**
     * Like [generate] but also returns the intermediate candidates, critic scores and repair state
     * so the pipeline trace can show what alternatives were considered. The `reply` field carries
     * the final sanitized reply (null when none could be produced).
     */
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

        val hasTools = prompt.webSearch || prompt.repoLookup || prompt.appContext || prompt.chatCommands
        val raw = when {
            hasTools -> generateWithTools(
                roomTarget,
                trigger,
                prompt,
                completedStages,
                configRevisionId,
            )
            llmProperties.critic.enabled -> generateWithCritic(roomTarget, prompt)
            else -> generateSingle(prompt)
        }

        val chosen = raw.reply ?: return raw.copy(reply = null)
        val reply = sanitize(chosen)
        if (reply.isBlank()) {
            LOGGER.warn { "Reply generator produced blank output for $roomTarget" }
            return raw.copy(reply = null)
        }
        return raw.copy(reply = reply)
    }

    /**
     * Agentic tool-loop generation: the model is offered all available tools (web search, repo
     * lookup, app context) and runs in a tool loop with global round settings from
     * [LlmProperties.ToolLoop]. The model decides for itself whether to reach for a tool while
     * composing the answer — no critic candidates are drawn, keeping cost bounded.
     */
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
            if (prompt.appContext) add(ToolCapability.APPLICATION)
            if (prompt.chatCommands) add(ToolCapability.CHAT_COMMAND)
        }
        val offeredTools = buildList {
            addAll(toolDispatcher.definitions(executionContext, capabilities))
            if (prompt.webSearch) add(org.taonity.sinairllmbot.bot.client.Tool.webSearch())
        }
        val toolLoop = llmProperties.toolLoop
        val content = llmClient.completeWithTools(
            tierName = toolLoop.tier.ifBlank { prompt.tierName },
            messages = listOf(ChatMessage.system(prompt.system), prompt.userMessage),
            tools = offeredTools,
            maxRounds = toolLoop.maxRounds,
            toolExecutor = { name, arguments ->
                toolDispatcher.execute(executionContext, name, arguments)
            },
        )?.content?.trim()?.takeIf { it.isNotBlank() }

        if (content == null) {
            LOGGER.warn { "Tool-grounded reply produced no content for $roomTarget" }
            return ReplyGeneration(reply = null)
        }
        return ReplyGeneration(
            reply = content,
            chosenIndex = 0,
            candidates = listOf(CandidateTrace(text = content, chosen = true)),
        )
    }

    private fun generateSingle(prompt: ReplyPrompt): ReplyGeneration {
        val content = llmClient.complete(
            tierName = prompt.tierName,
            messages = listOf(ChatMessage.system(prompt.system), prompt.userMessage),
            webSearch = prompt.webSearch,
        )?.content
        val candidates = content?.trim()?.takeIf { it.isNotBlank() }
            ?.let { listOf(CandidateTrace(text = it, chosen = true)) }
            ?: emptyList()
        return ReplyGeneration(reply = content, chosenIndex = content?.let { 0 }, candidates = candidates)
    }

    private fun generateWithCritic(roomTarget: String, prompt: ReplyPrompt): ReplyGeneration {
        val candidates = candidateGenerator.generate(prompt)
        if (candidates.isEmpty()) {
            LOGGER.warn { "No reply candidates for $roomTarget" }
            return ReplyGeneration(reply = null)
        }
        if (candidates.size == 1) {
            return ReplyGeneration(
                reply = candidates.first(),
                chosenIndex = 0,
                candidates = listOf(CandidateTrace(text = candidates.first(), chosen = true)),
            )
        }

        // Fail open: if the critic is unavailable or returns junk, keep the first candidate rather
        // than dropping the reply entirely.
        val verdict = replyCritic.evaluate(prompt, candidates) ?: run {
            LOGGER.info { "Critic unavailable for $roomTarget, using first candidate" }
            return ReplyGeneration(
                reply = candidates.first(),
                chosenIndex = 0,
                candidates = candidates.mapIndexed { i, c -> CandidateTrace(text = c, chosen = i == 0) },
            )
        }

        val bestIndex = verdict.best
        val best = candidates[bestIndex]
        val traces = candidates.mapIndexed { i, c ->
            val score = verdict.scores.getOrNull(i)
            CandidateTrace(
                text = c,
                chosen = i == bestIndex,
                fit = score?.fit,
                persona = score?.persona,
                risk = score?.risk,
                overall = score?.overall,
            )
        }
        val feedback = verdict.feedback.ifBlank { null }
        LOGGER.info {
            "Critic picked candidate $bestIndex/${candidates.size} " +
                "(overall=${verdict.bestOverall()}, needsRepair=${verdict.needsRepair}) for $roomTarget"
        }

        val needsRepair = verdict.needsRepair || verdict.bestOverall() < llmProperties.critic.repairThreshold
        if (!needsRepair) {
            return ReplyGeneration(reply = best, chosenIndex = bestIndex, criticUsed = true, criticFeedback = feedback, candidates = traces)
        }

        val repaired = repair(prompt, best, verdict.feedback)
            ?: return ReplyGeneration(reply = best, chosenIndex = bestIndex, criticUsed = true, criticFeedback = feedback, candidates = traces)

        // Re-critique original best vs repaired and keep the winner, so a repair can never regress.
        val recheck = replyCritic.evaluate(prompt, listOf(best, repaired))
        return if (recheck?.best == 1) {
            LOGGER.info { "Repaired reply accepted for $roomTarget" }
            ReplyGeneration(reply = repaired, chosenIndex = bestIndex, repaired = true, criticUsed = true, criticFeedback = feedback, candidates = traces)
        } else {
            LOGGER.info { "Repaired reply rejected for $roomTarget, keeping original best" }
            ReplyGeneration(reply = best, chosenIndex = bestIndex, criticUsed = true, criticFeedback = feedback, candidates = traces)
        }
    }

    private fun repair(prompt: ReplyPrompt, draft: String, feedback: String): String? {
        val instruction = buildString {
            append("Your draft reply was:\n").append(draft).append("\n\n")
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
        return llmClient.complete(tierName = prompt.tierName, messages = messages, webSearch = prompt.webSearch)
            ?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun sanitize(raw: String): String {
        var text = raw.trim().trim('"').trim()
        val selfPrefix = "${botProperties.persona.name}:"
        if (text.startsWith(selfPrefix, ignoreCase = true)) {
            text = text.substring(selfPrefix.length).trim()
        }
        // Collapse blank-line paragraph breaks into a single newline: chat participants don't
        // double-space their messages, and the extra gap looks off.
        text = text.replace(Regex("\\n[ \\t]*\\n+"), "\n")
        val maxReplyChars = botProperties.limits.maxReplyChars
        if (text.length > maxReplyChars) {
            text = text.take(maxReplyChars).trimEnd() + "\u2026"
        }
        return text
    }
}

/**
 * Result of a reply generation, carrying the final [reply] plus the candidates and critic state the
 * pipeline chose between so a trace can display the alternatives.
 */
data class ReplyGeneration(
    val reply: String?,
    val candidates: List<CandidateTrace> = emptyList(),
    val chosenIndex: Int? = null,
    val repaired: Boolean = false,
    val criticUsed: Boolean = false,
    val criticFeedback: String? = null,
)

/** One candidate reply the generator produced, with the critic's per-candidate scores when rated. */
data class CandidateTrace(
    val text: String,
    val chosen: Boolean = false,
    val fit: Int? = null,
    val persona: Int? = null,
    val risk: Int? = null,
    val overall: Int? = null,
)
