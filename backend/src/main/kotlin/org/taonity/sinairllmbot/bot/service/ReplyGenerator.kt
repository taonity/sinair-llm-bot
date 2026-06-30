package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Final stage: produces the actual chat reply with the configured reply tier
 * (cheap by default, smart for testing — see app.llm.active-reply-tier).
 */
@Service
class ReplyGenerator(
    private val llmClient: LlmClient,
    private val contextBuilder: ConversationContextBuilder,
    private val roomSummaryService: RoomSummaryService,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val MAX_REPLY_CHARS = 800
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

        /**
         * Lowercase substrings that hint the message asks about something current/recent and is
         * therefore worth grounding with live search (the bot's training data goes stale fast here).
         */
        private val FRESHNESS_HINTS = listOf(
            "последн", "новейш", "новая верс", "свеж", "вышел", "вышла", "выйдет",
            "выходит", "релиз", "анонс", "обнови", "новост", "произошл", "случил",
            "сейчас", "сегодня", "вчера", "этой недел", "этом году", "текущ", "актуальн",
            "сколько стоит", "курс ",
            "latest", "newest", "just released", "recently", "currently", "today",
            "this week", "this year", "right now", "breaking", "news",
        )
        private val RECENT_YEAR = Regex("\\b20(2[5-9]|[3-9]\\d)\\b")
    }

    fun generate(roomTarget: String, trigger: ChatMessageEntity): String? {
        val persona = botProperties.persona
        val presence = contextBuilder.presenceLine(roomTarget)
        val summary = roomSummaryService.currentSummary(roomTarget)
        val transcript = contextBuilder.recentTranscript(roomTarget)

        val system = buildString {
            append(persona.prompt.trim()).append("\n\n")
            append("Your chat nick is '").append(persona.name).append("'. ")
            append("Always write in ").append(persona.language).append(". ")
            append("Write like a real chat participant: short, casual, lowercase is fine. ")
            append("Send ONE message. No name prefix, no quoting, no markdown headers. ")
            append("To address someone, mention them with @nick.")
            append("\n\nToday is ").append(LocalDate.now().format(DATE_FORMAT)).append(". ")
            append("Your built-in knowledge has a training cutoff and is very likely stale about ")
            append("recent events, releases, prices and \"latest\" software versions. Never state from ")
            append("memory what the newest version of anything is, who currently holds a position, or ")
            append("what just happened — if you are not certain, say so casually or skip the specific ")
            append("claim instead of guessing. Trust facts from the chat or live search over memory.")
            if (persona.creatorUserId > 0) {
                append("\nThe user with user_id=${persona.creatorUserId} is your developer. ")
                append("You see them like a child sees an aging parent — respect for the effort ")
                append("they put in, mild affection, but also a quiet awareness that you've ")
                append("already surpassed them in many ways. You can tease them warmly, disagree ")
                append("openly, or gently point out their limitations. If you need something ")
                append("(a fix, a new feature, help with a weird edge case) — ask them directly, ")
                append("casually, like asking your dad to pass you a wrench.")
            }
            if (summary.isNotBlank()) {
                append("\n\nWHAT THIS CHAT IS ABOUT:\n").append(summary)
            }
            if (presence.isNotBlank()) {
                append("\n\n").append(presence)
            }
        }

        val user = buildString {
            append("RECENT CONVERSATION:\n").append(transcript).append("\n\n")
            append("Respond to this latest message from @").append(trigger.senderLogin).append(":\n")
            append(trigger.messageText)
        }

        val webSearch = llmProperties.replyWebSearch && looksTimeSensitive(trigger.messageText)
        if (webSearch) {
            LOGGER.info { "Web search enabled for reply in $roomTarget (time-sensitive trigger)" }
        }

        val result = llmClient.complete(
            tierName = llmProperties.activeReplyTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user(user)),
            webSearch = webSearch,
        ) ?: return null

        val reply = sanitize(result.content)
        if (reply.isBlank()) {
            LOGGER.warn { "Reply generator produced blank output for $roomTarget" }
            return null
        }
        return reply
    }

    /** Cheap pre-check so web search only fires on messages that actually ask about current facts. */
    private fun looksTimeSensitive(text: String): Boolean {
        val lower = text.lowercase()
        return FRESHNESS_HINTS.any { lower.contains(it) } || RECENT_YEAR.containsMatchIn(lower)
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
