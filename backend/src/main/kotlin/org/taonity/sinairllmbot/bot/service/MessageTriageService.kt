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
 * Cheap second stage: a single "gate"-tier call that triages the current conversation and answers
 * two questions at once, keeping tokens minimal with a tiny prompt and strict JSON output:
 *
 *  - [TriageVerdict.respond]       — should the bot jump in now? This deliberately includes
 *    *indirect* addressing (a follow-up to, reply to, or challenge of the bot's own last message,
 *    or a question the bot is clearly expected to field) — cases the [HeuristicGate] cannot catch
 *    because no name/alias is present.
 *  - [TriageVerdict.needsFreshInfo] — would answering well require up-to-date information (latest
 *    versions, current events, prices, "newest" anything)? Drives whether the reply enables live
 *    web search, since the bot's built-in knowledge goes stale on these topics.
 *
 * The bot's own messages appear in the transcript under its nick, which is what lets the model
 * recognise follow-ups aimed at the bot without an explicit mention.
 */
@Service
class MessageTriageService(
    private val llmClient: LlmClient,
    private val contextBuilder: ConversationContextBuilder,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val JSON_FENCE = Regex("^```(?:json)?|```$", RegexOption.IGNORE_CASE)
    }

    fun assess(roomTarget: String): TriageVerdict {
        val transcript = contextBuilder.recentTranscript(roomTarget, limit = 12)
        if (transcript.isBlank()) return TriageVerdict(respond = false, reason = "empty")

        val persona = botProperties.persona
        val aliases = (listOf(persona.name) + persona.aliases).joinToString(", ")
        val system = buildString {
            append("You are the gatekeeper for a chat bot in a ").append(persona.language)
            append(" group chat. The bot's nick is '").append(persona.name)
            append("' (also called: ").append(aliases).append("). In the transcript the bot's own ")
            append("messages appear under that nick. Judge the LATEST message and decide two things.\n\n")
            append("1) respond (boolean): should the bot send a message now? Say TRUE when the ")
            append("latest message is aimed at the bot even without naming it — a follow-up or reply ")
            append("to something the bot just said, a question the bot is clearly expected to answer, ")
            append("or someone reacting to / challenging / continuing the bot's previous message. ")
            append("Also TRUE when the bot could add genuine value, a real answer, a joke or a strong ")
            append("opinion to an open question. Say FALSE for small talk between other people, bare ")
            append("acknowledgements, or when a reply would add nothing. When in doubt but the message ")
            append("plausibly expects the bot, lean TRUE.\n")
            append("2) needsFreshInfo (boolean): would answering well require up-to-date information ")
            append("that changes over time — latest software versions, current events, recent releases, ")
            append("prices, 'newest'/'current' anything, who holds a role right now, today's facts? ")
            append("TRUE if the answer depends on knowledge that may be outdated, FALSE otherwise.\n\n")
            append("Respond with ONLY a JSON object: ")
            append("{\"respond\": boolean, \"needsFreshInfo\": boolean, \"reason\": string}. ")
            append("Default respond=false.")
        }
        val raw = llmClient.complete(
            tierName = llmProperties.gateTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user("RECENT CHAT:\n$transcript")),
            forceJson = true,
        ) ?: return TriageVerdict(respond = false, reason = "llm-unavailable")

        return parse(raw.content)
    }

    private fun parse(content: String): TriageVerdict {
        val cleaned = content.trim().lines()
            .filterNot { JSON_FENCE.containsMatchIn(it.trim()) && it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        return try {
            objectMapper.readValue(cleaned, TriageVerdict::class.java)
        } catch (exception: Exception) {
            LOGGER.warn { "Failed to parse triage verdict: '$content' (${exception.message})" }
            TriageVerdict(respond = false, reason = "parse-error")
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TriageVerdict(
    val respond: Boolean = false,
    val needsFreshInfo: Boolean = false,
    val reason: String = "",
)
