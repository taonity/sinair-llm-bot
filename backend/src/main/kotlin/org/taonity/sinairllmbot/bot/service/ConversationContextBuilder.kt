package org.taonity.sinairllmbot.bot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Duration
import java.time.Instant

/**
 * Builds the compact, token-efficient context fed to the LLM:
 *  - a one-line presence summary derived from [ChatEventEntity] (who is around, who is moder/owner),
 *  - the last N chat messages rendered as a plain transcript.
 *
 * Full history is never sent; the rolling room summary (see RoomSummaryService) carries older context.
 *
 * The transcript is time-aware: when a long quiet gap separates two consecutive messages, a
 * `--- ~Xh later ---` marker is inserted. This stops the model from reading days-old backlog as if
 * it were the live exchange (which made the bot resurrect long-finished topics), and lets triage
 * and reply generation treat the segment after the last gap as "what's happening now".
 */
@Component
class ConversationContextBuilder(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val botProperties: BotProperties,
) {
    private companion object {
        private val PRESENT_STATUSES = setOf("online", "back", "away")
        private const val EVENT_SCAN_LIMIT = 120
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

    /** Coarse, human-readable gap size for a session-break marker, e.g. "~40m", "~3h", "~2d". */
    private fun describeGap(gap: Duration): String {
        val minutes = gap.toMinutes()
        return when {
            minutes < 60 -> "~${minutes}m"
            minutes < 60 * 24 -> "~${gap.toHours()}h"
            else -> "~${gap.toDays()}d"
        }
    }

    /**
     * Raw text of the last [limit] messages (chronological). Used to scan the live segment for URLs
     * to ground on, so a link posted a message or two before the trigger (e.g. "here's the link" →
     * "try again") is still fetched rather than ignored.
     */
    fun recentMessageTexts(roomTarget: String, limit: Int): List<String> =
        chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, limit))
            .asReversed()
            .map { it.messageText }

    /** e.g. "Online now: DJ1, aps, Dr.Admin(moder). " — empty string when unknown. */
    fun presenceLine(roomTarget: String): String {
        val events = chatEventRepository
            .findByRoomTargetOrderByEventTimeDesc(roomTarget, PageRequest.of(0, EVENT_SCAN_LIMIT))

        // Keep only the latest event per member to know their current status.
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
