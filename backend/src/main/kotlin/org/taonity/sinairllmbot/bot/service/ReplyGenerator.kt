package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

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

        val result = llmClient.complete(
            tierName = llmProperties.activeReplyTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user(user)),
        ) ?: return null

        val reply = sanitize(result.content)
        if (reply.isBlank()) {
            LOGGER.warn { "Reply generator produced blank output for $roomTarget" }
            return null
        }
        return reply
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
