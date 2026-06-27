package org.taonity.sinairllmbot.bot.service

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository

/**
 * Builds the compact, token-efficient context fed to the LLM:
 *  - a one-line presence summary derived from [ChatEventEntity] (who is around, who is moder/owner),
 *  - the last N chat messages rendered as a plain transcript.
 *
 * Full history is never sent; the rolling room summary (see RoomSummaryService) carries older context.
 */
@Component
class ConversationContextBuilder(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val botProperties: BotProperties,
) {
    private companion object {
        // Statuses that mean the member is currently present.
        private val PRESENT_STATUSES = setOf("online", "back", "away")
        private const val EVENT_SCAN_LIMIT = 120
    }

    fun recentTranscript(roomTarget: String, limit: Int = botProperties.context.recentMessageCount): String {
        val maxChars = botProperties.context.maxMessageChars
        val messages = chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, limit))
            .asReversed()
        return messages.joinToString("\n") { msg ->
            val text = msg.messageText.let { if (it.length > maxChars) it.take(maxChars) + "…" else it }
            val userIdTag = if (msg.senderUserId > 0) "[uid:${msg.senderUserId}]" else ""
            "${msg.senderLogin}$userIdTag: ${text.replace("\n", " ")}"
        }
    }

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
