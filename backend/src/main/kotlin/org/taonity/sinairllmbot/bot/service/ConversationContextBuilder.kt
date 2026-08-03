package org.taonity.sinairllmbot.bot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Duration
import java.time.Instant

@Component
class ConversationContextBuilder(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()

    private companion object {
        private val PRESENT_STATUSES = setOf("online", "back", "away")
    }

    fun recentTranscript(roomTarget: String, limit: Int = botProperties.context.recentMessageCount): String {
        val maxChars = botProperties.context.maxMessageChars
        val gapThreshold = Duration.ofMinutes(botProperties.context.sessionGapMinutes)
        val messages = chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, limit))
            .asReversed()

        val builder = StringBuilder()
        var previousSentAt: Instant? = null
        for (msg in messages) {
            previousSentAt?.let { prev ->
                val gap = Duration.between(prev, msg.sentAt)
                if (gap >= gapThreshold) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append("--- ").append(describeGap(gap)).append(" later ---")
                }
            }
            if (builder.isNotEmpty()) builder.append('\n')
            val text = msg.messageText.let { if (it.length > maxChars) it.take(maxChars) + "…" else it }
            val userIdTag = if (msg.senderUserId > 0) "[uid:${msg.senderUserId}]" else ""
            builder.append("${msg.senderLogin}$userIdTag: ${text.replace("\n", " ")}")
            previousSentAt = msg.sentAt
        }
        return builder.toString()
    }

    private fun describeGap(gap: Duration): String {
        val minutes = gap.toMinutes()
        return when {
            minutes < 60 -> "~${minutes}m"
            minutes < 60 * 24 -> "~${gap.toHours()}h"
            else -> "~${gap.toDays()}d"
        }
    }

    fun recentMessageTexts(roomTarget: String, limit: Int): List<String> =
        chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, limit))
            .asReversed()
            .map { it.messageText }

    fun presenceLine(roomTarget: String): String {
        val events = chatEventRepository
            .findByRoomTargetOrderByEventTimeDesc(roomTarget, PageRequest.of(0, botProperties.limits.eventScanLimit))

        val latestByMember = LinkedHashMap<Int, ChatEventEntity>()
        for (event in events) {
            latestByMember.putIfAbsent(event.memberId, event)
        }

        val present = latestByMember.values
            .filter { it.status in PRESENT_STATUSES }
            .map { event ->
                val role = when {
                    event.isOwner -> "(owner)"
                    event.isModer -> "(moder)"
                    else -> ""
                }
                "${event.memberName}$role"
            }
            .distinct()

        return if (present.isEmpty()) "" else "Online now: ${present.joinToString(", ")}."
    }
}
