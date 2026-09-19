package org.taonity.sinairllmbot.bot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Duration
import java.time.Instant
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

@Component
class ConversationContextBuilder(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val settings: BotSettings,
    private val outboundMessageRepository: OutboundMessageRepository,
) {
    private val botProperties get() = settings.bot()

    private companion object {
        private val PRESENT_STATUSES = setOf("online", "back", "away")
    }

    fun recentTranscript(roomTarget: String, limit: Int = botProperties.context.recentMessageCount): String {
        val outbound = outboundMessageRepository.findRecentUnechoedReplies(
            roomTarget,
            Instant.now().minus(Duration.ofMinutes(botProperties.context.sessionGapMinutes)),
            PageRequest.of(0, limit),
        )
        val messages = chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, limit))
            .asReversed()
        val echoedIds = messages.mapNotNull { it.sourceOutboundMessageId }.toSet()
        val unechoed = outbound.filter { it.id !in echoedIds }
        val entries = (messages.map(::chatEntry) + unechoed.map(::outboundEntry))
            .sortedBy { it.at }.takeLast(limit)
        val transcript = render(entries)
        return if (unechoed.isEmpty()) transcript else
            "BOT OUTPUT CONTEXT: bot=self marks your own output, even under an older nickname. " +
                "Local outbound entries are replies already prepared for their reply-to target; do not queue " +
                "the same answer again. PENDING/CLAIMED are not proof that anyone has seen the reply. " +
                "SENT means collector acknowledgement, not a confirmed chat echo. New follow-ups still need assessment.\n" + transcript
    }

    fun formatTranscript(messages: List<ChatMessageEntity>): String = render(messages.map(::chatEntry))

    private data class TranscriptEntry(val at: Instant, val header: String, val text: String)

    private fun chatEntry(message: ChatMessageEntity): TranscriptEntry {
        val userIdTag = if (message.senderUserId > 0) "[uid:${message.senderUserId}]" else ""
        val selfTag = message.sourceOutboundMessageId?.let { "[bot=self outbound:$it]" }.orEmpty()
        return TranscriptEntry(message.sentAt, "[id:${message.id} at:${message.sentAt}] ${message.senderLogin}$userIdTag$selfTag:", message.messageText)
    }

    private fun outboundEntry(message: OutboundMessageEntity): TranscriptEntry {
        val delivery = when (message.status) {
            OutboundStatus.PENDING -> "queued; delivery not confirmed"
            OutboundStatus.CLAIMED -> "claimed by collector; delivery not confirmed"
            OutboundStatus.SENT -> "collector acknowledged; chat echo not yet ingested"
        }
        return TranscriptEntry(
            message.createdAt,
            "[outbound:${message.id} at:${message.createdAt} reply-to:${message.triggerMessageId}] " +
                "BOT [bot=self status:${message.status} $delivery]:",
            message.messageText,
        )
    }

    private fun render(entries: List<TranscriptEntry>): String {
        val maxChars = botProperties.context.maxMessageChars
        val gapThreshold = Duration.ofMinutes(botProperties.context.sessionGapMinutes)
        val builder = StringBuilder()
        var previousSentAt: Instant? = null
        for (entry in entries) {
            previousSentAt?.let { prev ->
                val gap = Duration.between(prev, entry.at)
                if (gap >= gapThreshold) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append("--- ").append(describeGap(gap)).append(" later ---")
                }
            }
            if (builder.isNotEmpty()) builder.append('\n')
            val text = entry.text.let {
                if (it.length > maxChars) it.take(maxChars / 2) + "\n[earlier detail omitted]\n" + it.takeLast(maxChars / 2) else it
            }
            builder.append(entry.header).append('\n').append(text)
            previousSentAt = entry.at
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
