package org.taonity.sinairllmbot.bot.service

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper

/**
 * Cheap second stage: a single "gate"-tier call that triages the current conversation and answers
 * two questions at once, keeping tokens minimal with a tiny prompt and strict JSON output:
 *
 *  - [TriageVerdict.respond]       — should the bot jump in now? This is the sole intent judge (the
 *    [CommandGate] only catches mute/un-mute commands). It fires ONLY when the bot is addressed
 *    directly (by nick/@mention/alias) or the latest message is an unmistakable direct follow-up or
 *    reply to the bot's OWN last message. A general/open question thrown out to the room that any
 *    member could answer does NOT fire it — people name the bot when they actually want its input.
 *    It also fires when the latest message states a clear, objective factual falsehood the bot can
 *    correct (real checkable facts only, not opinions/jokes). It deliberately does NOT fire just
 *    because the bot could add an opinion or a joke — the bot stays quiet unless it is actually being
 *    addressed or genuine misinformation needs correcting.
 *  - [TriageVerdict.needsFreshInfo] — would answering well require up-to-date information (latest
 *    versions, current events, prices, "newest" anything)? Drives whether the reply enables live
 *    web search, since the bot's built-in knowledge goes stale on these topics.
 *  - [TriageVerdict.needsSearch] — is the latest message asking the bot to look up, find or describe
 *    a specific named thing it likely cannot answer accurately from memory (a site, product, tool,
 *    game, term, place, person or org), or telling it to go ahead with / retry such a look-up asked
 *    of it just before? Also enables live web search, independent of time-sensitivity, so an
 *    explicit research request grounds in real results instead of the model's priors.
 *
 * The bot's own messages appear in the transcript under its nick, which is what lets the model
 * recognise follow-ups aimed at the bot without an explicit mention.
 */
@Service
class MessageTriageService(
    private val llmClient: LlmClient,
    private val contextBuilder: ConversationContextBuilder,
    private val settings: BotSettings,
    private val objectMapper: ObjectMapper,
) {
    private val botProperties get() = settings.bot()
    private val llmProperties get() = settings.llm()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val JSON_FENCE = Regex("^```(?:json)?|```$", RegexOption.IGNORE_CASE)

        // Salvage regexes: recover the two booleans even from truncated/misspelled JSON, so a
        // response the model meant as respond=true is never silently downgraded to false. The
        // `needs?FreshInfo` alternation tolerates the model dropping the 's'.
        private val RESPOND_REGEX = Regex("\"respond\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        private val FRESH_REGEX = Regex("\"needs?FreshInfo\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        private val SEARCH_REGEX = Regex("\"needs?Search\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
    }

    fun assess(roomTarget: String): TriageVerdict {
        val transcript = contextBuilder.recentTranscript(roomTarget, limit = 12)
        if (transcript.isBlank()) return TriageVerdict(respond = false)

        val persona = botProperties.persona
        val aliases = (listOf(persona.name) + persona.aliases).joinToString(", ")
        val system = buildString {
            append("You are the gatekeeper for a chat bot in a ").append(persona.language)
            append(" group chat. The bot's nick is '").append(persona.name)
            append("' (also called: ").append(aliases).append("). In the transcript the bot's own ")
            append("messages appear under that nick. Judge the LATEST message and decide two things.\n\n")
            append("1) respond (boolean): should the bot send a message now? Say TRUE ONLY when the ")
            append("latest message is genuinely aimed at THIS bot in particular — either it addresses ")
            append("the bot directly (by its nick, an @mention or one of its aliases) or it is an ")
            append("unmistakable direct follow-up or reply to something the bot ITSELF just said in ")
            append("the transcript. A general or open question thrown out to the chat that any member ")
            append("could answer is NOT for the bot: say FALSE even if the bot happens to know the ")
            append("answer or could answer it well. People here name the bot when they actually want ")
            append("its input, so if the bot is not named and the message is not a direct reply to the ")
            append("bot's own last message, assume it is meant for the other people in the chat. ALSO ")
            append("say TRUE when the latest message states a clear, objective factual falsehood that ")
            append("could genuinely mislead people and the bot can correct it — only for real, ")
            append("checkable facts, NOT opinions, jokes, exaggeration, sarcasm or debatable claims. ")
            append("Say FALSE for everything else: small talk between other people, general remarks or ")
            append("open questions to the room, bare acknowledgements, noise. Do NOT respond just to ")
            append("add an opinion, a joke, to be helpful, or to seem present. When in doubt, say FALSE.\n")
            append("2) needsFreshInfo (boolean): would answering well require up-to-date information ")
            append("that changes over time — latest software versions, current events, recent releases, ")
            append("prices, 'newest'/'current' anything, who holds a role right now, today's facts? ")
            append("TRUE if the answer depends on knowledge that may be outdated, FALSE otherwise.\n")
            append("3) needsSearch (boolean): would answering well require looking something up on the ")
            append("web right now? Say TRUE when the latest message asks the bot to look up, find, ")
            append("google, check or describe a specific named thing it likely cannot answer ")
            append("accurately from memory — a website, product, service, tool, game, library, term, ")
            append("place, person or organization — or when it tells the bot to go ahead with, retry ")
            append("or continue such a look-up that was asked of it a moment earlier. Say FALSE for ")
            append("general chit-chat, opinions, or questions the bot can answer well from its own ")
            append("knowledge.\n")
            append("Also classify the decision with category (string) = exactly one of: ")
            append("direct_address (the message names, @mentions or uses an alias of the bot), ")
            append("indirect_address (an unmistakable direct follow-up or reply to the bot's OWN last ")
            append("message, without naming it), ")
            append("misinformation (you would answer only to correct a checkable factual falsehood), ")
            append("not_addressed (the message is not aimed at the bot), ")
            append("noise (a bare acknowledgement, filler or noise). Choose the single closest kind; it ")
            append("must be one of those exact tokens and must NOT contain any words from the ")
            append("conversation or restate its topic.\n")
            append("Respond with ONLY a JSON object, booleans first: ")
            append("{\"respond\": boolean, \"needsFreshInfo\": boolean, \"needsSearch\": boolean, ")
            append("\"category\": string}. Default respond=false.")
        }
        val raw = llmClient.complete(
            tierName = llmProperties.gateTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user("RECENT CHAT:\n$transcript")),
            forceJson = true,
        ) ?: return TriageVerdict(respond = false)

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
            salvage(cleaned) ?: run {
                LOGGER.warn(exception) { "Failed to parse triage verdict (len=${content.length})" }
                TriageVerdict(respond = false)
            }
        }
    }

    /**
     * Best-effort recovery when strict JSON parsing fails (usually truncated output that hit the
     * tier's token cap mid-string). Returns null only when not even `respond` can be recovered.
     */
    private fun salvage(text: String): TriageVerdict? {
        val respond = RESPOND_REGEX.find(text)?.groupValues?.get(1)?.equals("true", ignoreCase = true)
            ?: return null
        val needsFresh = FRESH_REGEX.find(text)?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        val needsSearch = SEARCH_REGEX.find(text)?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        LOGGER.info { "Salvaged truncated triage verdict: respond=$respond needsFreshInfo=$needsFresh needsSearch=$needsSearch" }
        return TriageVerdict(respond = respond, needsFreshInfo = needsFresh, needsSearch = needsSearch)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TriageVerdict(
    val respond: Boolean = false,
    @JsonAlias("needFreshInfo", "freshInfo", "needs_fresh_info", "need_fresh_info")
    val needsFreshInfo: Boolean = false,
    @JsonAlias("needSearch", "search", "needs_search", "need_search")
    val needsSearch: Boolean = false,
    val category: String = "",
) {
    /**
     * Topic-free kind of decision safe to log. Whitelisted so no free-form model text (which could
     * echo the conversation) ever reaches the logs — anything unrecognized becomes "unclassified".
     */
    val loggableCategory: String
        get() = category.trim().lowercase().takeIf { it in ALLOWED_CATEGORIES } ?: "unclassified"

    private companion object {
        val ALLOWED_CATEGORIES = setOf(
            "direct_address", "indirect_address", "misinformation", "not_addressed", "noise",
        )
    }
}
