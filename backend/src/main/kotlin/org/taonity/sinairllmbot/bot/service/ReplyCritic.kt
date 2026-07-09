package org.taonity.sinairllmbot.bot.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import tools.jackson.databind.ObjectMapper

/**
 * Rates candidate replies against the same brief the generator saw (persona rules + conversation +
 * trigger), using a cheap JSON-only judge tier. Picks the best candidate and flags whether it needs
 * a repair pass, with concrete feedback on what to fix.
 *
 * Fails open: any missing/invalid verdict returns null so the caller keeps a candidate instead of
 * dropping the reply.
 */
@Service
class ReplyCritic(
    private val llmClient: LlmClient,
    private val llmProperties: LlmProperties,
    private val botProperties: BotProperties,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        // Stable marker so local WireMock stubs can distinguish critic calls from the triage classifier.
        private const val MARKER = "REPLY CRITIC"
        private val JSON_FENCE = Regex("^```(?:json)?|```$", RegexOption.IGNORE_CASE)
    }

    fun evaluate(prompt: ReplyPrompt, candidates: List<String>): CriticVerdict? {
        if (candidates.isEmpty()) return null

        val persona = botProperties.persona
        val system = buildString {
            append(MARKER).append(". You are a strict critic for a chat bot's reply. Below is the ")
            append("exact brief the bot was given (its persona and rules) and the recent conversation. ")
            append("The bot drafted several candidate replies. Rate EACH candidate 0-10 on:\n")
            append("- fit: does it actually answer/react to the latest message, stay on topic, and ")
            append("keep a natural chat length? If the latest message is a direct request or ")
            append("question addressed to the bot, fit MUST reward candidates that fully and ")
            append("correctly do what was asked, and heavily punish ones that dodge, half-answer, ")
            append("or reply with a clarifying/filler question instead of delivering the result (an ")
            append("arrogant nitpick is fine ONLY if the real answer is still there).\n")
            append("- persona: does it match the bot's style rules — casual, short, in ")
            append(persona.language).append(", ONE message, no markdown, no name prefix, never ")
            append("assistant-like or lecturing?\n")
            append("- risk: hallucination/safety risk. 0 = safe; 10 = states likely-false 'latest/")
            append("current' facts, invents APIs/features/prices, or breaks the rules.\n")
            append("overall (0-10) should reward high fit and persona and punish risk. Pick the best ")
            append("candidate by 0-based index. Set needsRepair=true when even the best candidate is ")
            append("weak or violates a rule. In feedback, say concretely what to fix in the best ")
            append("candidate to make it a great chat reply (empty string if nothing to fix).\n")
            append("Respond with ONLY a JSON object: {\"scores\":[{\"fit\":n,\"persona\":n,\"risk\":n,")
            append("\"overall\":n}],\"best\":n,\"needsRepair\":boolean,\"feedback\":string}.\n\n")
            append("BOT BRIEF:\n").append(prompt.system)
        }

        val user = buildString {
            append(prompt.userText).append("\n\nCANDIDATE REPLIES:\n")
            candidates.forEachIndexed { index, candidate ->
                append("[").append(index).append("] ").append(candidate).append("\n")
            }
        }

        val raw = llmClient.complete(
            tierName = llmProperties.criticTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user(user)),
            forceJson = true,
        ) ?: return null

        return parse(raw.content, candidates.size)
    }

    private fun parse(content: String, candidateCount: Int): CriticVerdict? {
        val cleaned = content.trim().lines()
            .filterNot { JSON_FENCE.containsMatchIn(it.trim()) && it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        return try {
            val verdict = objectMapper.readValue(cleaned, CriticVerdict::class.java)
            verdict.copy(best = verdict.best.coerceIn(0, candidateCount - 1))
        } catch (exception: Exception) {
            LOGGER.warn { "Failed to parse critic verdict: '$content' (${exception.message})" }
            null
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CriticVerdict(
    val scores: List<CandidateScore> = emptyList(),
    val best: Int = 0,
    val needsRepair: Boolean = false,
    val feedback: String = "",
) {
    fun bestOverall(): Int = scores.getOrNull(best)?.overall ?: 0
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CandidateScore(
    val fit: Int = 0,
    val persona: Int = 0,
    val risk: Int = 0,
    val overall: Int = 0,
)
