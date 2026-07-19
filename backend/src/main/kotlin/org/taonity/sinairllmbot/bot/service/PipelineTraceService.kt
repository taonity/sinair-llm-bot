package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.bot.pipeline.PipelineKeys
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
    private val pipelineLlmUsageTracker: PipelineLlmUsageTracker,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val TRIGGER_TEXT_MAX = 2000
        private const val SYSTEM_ACTOR = "system"
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
            val llmUsage = pipelineLlmUsageTracker.drain()
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
                    totalTokens = llmUsage.sumOf { it.tokens },
                    llmUsageJson = objectMapper.writeValueAsString(llmUsage),
                ),
            )
        }.onFailure { LOGGER.warn(it) { "Failed to record pipeline trace for ${trigger.roomTarget}" } }
    }

    /**
     * Records a summary-refresh as its own pipeline run (no triggering message), so the console shows
     * it with the same detail as a reply run: its stages and the LLM call that produced it (with the
     * request/response payloads gathered by the usage tracker). Synthetic trigger fields carry the
     * room and a human label since a refresh is not tied to one message.
     */
    fun recordSummary(
        roomTarget: String,
        outcome: String,
        stages: List<PipelineStage>,
        outcomeDetail: String? = null,
    ) {
        runCatching {
            val llmUsage = pipelineLlmUsageTracker.drain()
            pipelineRunRepository.save(
                PipelineRunEntity(
                    pipelineKey = PipelineKeys.SUMMARY,
                    roomTarget = roomTarget,
                    triggerMessageId = null,
                    triggerSenderLogin = SYSTEM_ACTOR,
                    triggerText = "Summary refresh · $roomTarget",
                    outcome = outcome,
                    outcomeDetail = outcomeDetail,
                    outboundMessageId = null,
                    stagesJson = objectMapper.writeValueAsString(stages),
                    totalTokens = llmUsage.sumOf { it.tokens },
                    llmUsageJson = objectMapper.writeValueAsString(llmUsage),
                ),
            )
        }.onFailure { LOGGER.warn(it) { "Failed to record summary trace for $roomTarget" } }
    }
}
