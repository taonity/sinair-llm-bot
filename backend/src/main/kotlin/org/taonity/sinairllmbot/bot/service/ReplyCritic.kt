package org.taonity.sinairllmbot.bot.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.config.BotSettings
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
    private val settings: BotSettings,
    private val objectMapper: ObjectMapper,
) {
    private val llmProperties get() = settings.llm()
    private val botProperties get() = settings.bot()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        // Stable marker so local WireMock stubs can distinguish critic calls from the triage classifier.
        // Code-owned (not part of the editable template) so console edits can't break stub routing.
        private const val MARKER = "REPLY CRITIC"
        // Code-owned output contract appended after the template so the JSON schema can never be
        // edited away from the console (its removal would silently disable the critic).
        private const val JSON_CONTRACT =
            "Respond with ONLY a JSON object: {\"scores\":[{\"fit\":n,\"persona\":n,\"risk\":n," +
                "\"overall\":n}],\"best\":n,\"needsRepair\":boolean,\"feedback\":string}."
        private val JSON_FENCE = Regex("^```(?:json)?|```$", RegexOption.IGNORE_CASE)
    }

    fun evaluate(prompt: ReplyPrompt, candidates: List<String>): CriticVerdict? {
        if (candidates.isEmpty()) return null

        val system = buildString {
            append(MARKER).append(". ")
            append(render(llmProperties.critic.prompt, brief = prompt.system, language = botProperties.persona.language))
            append("\n\n").append(JSON_CONTRACT)
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

    /**
     * Substitutes the runtime placeholder tokens into the configurable critic rubric. Tokens are
     * guaranteed present by config-save validation (see ConfigRegistry.requireCriticPlaceholders).
     */
    private fun render(template: String, brief: String, language: String): String =
        template.trim()
            .replace("{language}", language)
            .replace("{brief}", brief)

    private fun parse(content: String, candidateCount: Int): CriticVerdict? {
        val cleaned = content.trim().lines()
            .filterNot { JSON_FENCE.containsMatchIn(it.trim()) && it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        return try {
            val verdict = objectMapper.readValue(cleaned, CriticVerdict::class.java)
            verdict.copy(best = verdict.best.coerceIn(0, candidateCount - 1))
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "Failed to parse critic verdict (len=${content.length})" }
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
