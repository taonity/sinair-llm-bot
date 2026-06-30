package org.taonity.sinairllmbot.console.dto

import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.console.entity.AuditLogEntity
import java.time.Instant

data class ChatMessageDto(
    val id: String?,
    val roomTarget: String,
    val senderLogin: String,
    val senderMemberId: Int,
    val messageText: String,
    val messageStyle: String,
    val sentAt: Instant,
    val receivedAt: Instant,
) {
    companion object {
        fun from(e: ChatMessageEntity) = ChatMessageDto(
            id = e.id,
            roomTarget = e.roomTarget,
            senderLogin = e.senderLogin,
            senderMemberId = e.senderMemberId,
            messageText = e.messageText,
            messageStyle = e.messageStyle,
            sentAt = e.sentAt,
            receivedAt = e.receivedAt,
        )
    }
}

data class ChatEventDto(
    val id: String?,
    val roomTarget: String,
    val memberName: String,
    val memberId: Int,
    val status: String,
    val eventData: String?,
    val eventTime: Instant,
    val receivedAt: Instant,
) {
    companion object {
        fun from(e: ChatEventEntity) = ChatEventDto(
            id = e.id,
            roomTarget = e.roomTarget,
            memberName = e.memberName,
            memberId = e.memberId,
            status = e.status,
            eventData = e.eventData,
            eventTime = e.eventTime,
            receivedAt = e.receivedAt,
        )
    }
}

data class OutboundMessageDto(
    val id: String?,
    val roomTarget: String,
    val messageText: String,
    val replyToExternalId: String?,
    val status: String,
    val createdAt: Instant,
    val claimedAt: Instant?,
    val sentAt: Instant?,
) {
    companion object {
        fun from(e: OutboundMessageEntity) = OutboundMessageDto(
            id = e.id,
            roomTarget = e.roomTarget,
            messageText = e.messageText,
            replyToExternalId = e.replyToExternalId,
            status = e.status.name,
            createdAt = e.createdAt,
            claimedAt = e.claimedAt,
            sentAt = e.sentAt,
        )
    }
}

data class RoomSummaryDto(
    val id: String?,
    val roomTarget: String,
    val summary: String,
    val messageCount: Int,
    val updatedAt: Instant,
) {
    companion object {
        fun from(e: RoomSummaryEntity) = RoomSummaryDto(
            id = e.id,
            roomTarget = e.roomTarget,
            summary = e.summary,
            messageCount = e.messageCount,
            updatedAt = e.updatedAt,
        )
    }
}

data class UpdateSummaryBody(
    val summary: String,
)

data class AuditLogDto(
    val id: String?,
    val action: String,
    val targetType: String,
    val targetId: String?,
    val actorEmail: String,
    val occurredAt: Instant,
) {
    companion object {
        fun from(e: AuditLogEntity) = AuditLogDto(
            id = e.id,
            action = e.action.name,
            targetType = e.targetType,
            targetId = e.targetId,
            actorEmail = e.actorEmail,
            occurredAt = e.occurredAt,
        )
    }
}
