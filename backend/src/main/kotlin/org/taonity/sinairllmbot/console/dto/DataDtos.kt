package org.taonity.sinairllmbot.console.dto

import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailure
import org.taonity.sinairllmbot.bot.pipeline.LlmCallUsage
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
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
    /** The pipeline run holding the source transcript, or null if never linked. */
    val pipelineRunId: String?,
    /** True while that run still exists (i.e. within the 7-day retention window). */
    val detailAvailable: Boolean,
    val history: List<SummaryVersionDto> = emptyList(),
)

data class SummaryVersionDto(
    val id: String?,
    val summary: String,
    val messageCount: Int,
    val createdAt: Instant,
    val pipelineRunId: String?,
    val detailAvailable: Boolean,
)

/**
 * A persisted pipeline run for the console. [stages] is the deserialized [stagesJson] of the entity
 * (parsed by the service, which has the ObjectMapper); the stage shape mirrors
 * [org.taonity.sinairllmbot.bot.pipeline.PipelineStage].
 */
data class PipelineRunDto(
    val id: String?,
    val pipelineKey: String,
    val roomTarget: String,
    val triggerMessageId: String?,
    val triggerSenderLogin: String,
    val triggerText: String,
    val outcome: String,
    val outcomeDetail: String?,
    val outboundMessageId: String?,
    val stages: List<PipelineStageDto>,
    val totalTokens: Int,
    val llmUsage: List<LlmCallUsageDto>,
    val jsonParseFailures: List<JsonParseFailureDto>,
    val createdAt: Instant,
) {
    companion object {
        fun from(
            e: PipelineRunEntity,
            stages: List<PipelineStageDto>,
            llmUsage: List<LlmCallUsageDto>,
            jsonParseFailures: List<JsonParseFailureDto>,
        ) =
            PipelineRunDto(
                id = e.id,
                pipelineKey = e.pipelineKey,
                roomTarget = e.roomTarget,
                triggerMessageId = e.triggerMessageId,
                triggerSenderLogin = e.triggerSenderLogin,
                triggerText = e.triggerText,
                outcome = e.outcome,
                outcomeDetail = e.outcomeDetail,
                outboundMessageId = e.outboundMessageId,
                stages = stages,
                totalTokens = e.totalTokens,
                llmUsage = llmUsage,
                jsonParseFailures = jsonParseFailures,
                createdAt = e.createdAt,
            )
    }
}

/** One failed JSON-deserialization attempt of a run's triage/critic prompt, with the raw payload. */
data class JsonParseFailureDto(
    val label: String = "",
    val attempt: Int = 0,
    val payload: String = "",
) {
    companion object {
        fun from(f: JsonParseFailure) = JsonParseFailureDto(label = f.label, attempt = f.attempt, payload = f.payload)
    }
}

data class LlmCallUsageDto(
    val tier: String = "",
    val model: String = "",
    val tokens: Int = 0,
    val tools: List<String> = emptyList(),
    val hasRequestPayload: Boolean = false,
    val hasResponsePayload: Boolean = false,
) {
    companion object {
        fun from(u: LlmCallUsage) = LlmCallUsageDto(
            tier = u.tier,
            model = u.model,
            tokens = u.tokens,
            tools = u.tools,
            hasRequestPayload = u.requestPayload.isNotBlank(),
            hasResponsePayload = u.responsePayload.isNotBlank(),
        )
    }
}

data class PipelineStageDto(
    val key: String = "",
    val label: String = "",
    val status: String = "",
    val summary: String = "",
    val fields: List<PipelineFieldDto> = emptyList(),
    val alternatives: List<PipelineAlternativeDto> = emptyList(),
)

data class PipelineFieldDto(
    val label: String = "",
    val value: String = "",
)

data class PipelineAlternativeDto(
    val text: String = "",
    val chosen: Boolean = false,
    val fields: List<PipelineFieldDto> = emptyList(),
)

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
