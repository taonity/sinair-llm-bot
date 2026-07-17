package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import tools.jackson.databind.ObjectMapper

/**
 * Persists pipeline-run traces so the console can show, per triggering message, every decision the
 * bot made and any alternatives it chose between. Purely observational and fail-open: a failure to
 * record a trace must never affect the reply pipeline.
 */
@Service
class PipelineTraceService(
    private val pipelineRunRepository: PipelineRunRepository,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val TRIGGER_TEXT_MAX = 2000
    }

    fun record(
        pipelineKey: String,
        trigger: ChatMessageEntity,
        outcome: String,
        stages: List<PipelineStage>,
        outcomeDetail: String? = null,
        outboundMessageId: String? = null,
    ) {
        runCatching {
            pipelineRunRepository.save(
                PipelineRunEntity(
                    pipelineKey = pipelineKey,
                    roomTarget = trigger.roomTarget,
                    triggerMessageId = trigger.id,
                    triggerSenderLogin = trigger.senderLogin,
                    triggerText = trigger.messageText.take(TRIGGER_TEXT_MAX),
                    outcome = outcome,
                    outcomeDetail = outcomeDetail,
                    outboundMessageId = outboundMessageId,
                    stagesJson = objectMapper.writeValueAsString(stages),
                ),
            )
        }.onFailure { LOGGER.warn(it) { "Failed to record pipeline trace for ${trigger.roomTarget}" } }
    }
}
