package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

/**
 * Final stage: produces the actual chat reply.
 *
 * When the critic layer is enabled (app.llm.critic.enabled) it draws several candidates, has a cheap
 * judge rate them against the same brief, keeps the best and repairs it once if it scores too low.
 * Otherwise it falls back to a single direct generation. Prompt assembly (persona + context + any
 * grounded link/image content, plus vision-tier routing) lives in [ReplyPromptBuilder].
 */
@Service
class ReplyGenerator(
    private val llmClient: LlmClient,
    private val promptBuilder: ReplyPromptBuilder,
    private val candidateGenerator: CandidateGenerator,
    private val replyCritic: ReplyCritic,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val MAX_REPLY_CHARS = 800
    }

    /**
     * @param needsFreshInfo the cheap triage stage's judgment that answering requires up-to-date
     *   information; enables live web search (see [ReplyPromptBuilder]).
     */
    fun generate(roomTarget: String, trigger: ChatMessageEntity, needsFreshInfo: Boolean = false): String? {
        val prompt = promptBuilder.build(roomTarget, trigger, needsFreshInfo)

        val chosen = if (llmProperties.critic.enabled) {
            generateWithCritic(roomTarget, prompt)
        } else {
            generateSingle(prompt)
        } ?: return null

        val reply = sanitize(chosen)
        if (reply.isBlank()) {
            LOGGER.warn { "Reply generator produced blank output for $roomTarget" }
            return null
        }
        return reply
    }

    private fun generateSingle(prompt: ReplyPrompt): String? =
        llmClient.complete(
            tierName = prompt.tierName,
            messages = listOf(ChatMessage.system(prompt.system), prompt.userMessage),
            webSearch = prompt.webSearch,
        )?.content

    private fun generateWithCritic(roomTarget: String, prompt: ReplyPrompt): String? {
        val candidates = candidateGenerator.generate(prompt)
        if (candidates.isEmpty()) {
            LOGGER.warn { "No reply candidates for $roomTarget" }
            return null
        }
        if (candidates.size == 1) return candidates.first()

        // Fail open: if the critic is unavailable or returns junk, keep the first candidate rather
        // than dropping the reply entirely.
        val verdict = replyCritic.evaluate(prompt, candidates) ?: run {
            LOGGER.info { "Critic unavailable for $roomTarget, using first candidate" }
            return candidates.first()
        }

        val best = candidates[verdict.best]
        LOGGER.info {
            "Critic picked candidate ${verdict.best}/${candidates.size} " +
                "(overall=${verdict.bestOverall()}, needsRepair=${verdict.needsRepair}) for $roomTarget"
        }

        val needsRepair = verdict.needsRepair || verdict.bestOverall() < llmProperties.critic.repairThreshold
        if (!needsRepair) return best

        val repaired = repair(prompt, best, verdict.feedback) ?: return best

        // Re-critique original best vs repaired and keep the winner, so a repair can never regress.
        val recheck = replyCritic.evaluate(prompt, listOf(best, repaired))
        return if (recheck?.best == 1) {
            LOGGER.info { "Repaired reply accepted for $roomTarget" }
            repaired
        } else {
            LOGGER.info { "Repaired reply rejected for $roomTarget, keeping original best" }
            best
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
        return llmClient.complete(tierName = prompt.tierName, messages = messages)
            ?.content?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun sanitize(raw: String): String {
        var text = raw.trim().trim('"').trim()
        val selfPrefix = "${botProperties.persona.name}:"
        if (text.startsWith(selfPrefix, ignoreCase = true)) {
            text = text.substring(selfPrefix.length).trim()
        }
        if (text.length > MAX_REPLY_CHARS) {
            text = text.take(MAX_REPLY_CHARS).trimEnd() + "…"
        }
        return text
    }
}
