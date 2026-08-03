package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailureTracker
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.pipeline.PipelineKeys
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.service.ConfigRevisionService
import tools.jackson.databind.ObjectMapper

// Tracing is observational and must fail open without affecting replies.
@Service
class PipelineTraceService(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pipelineLlmUsageTracker: PipelineLlmUsageTracker,
    private val jsonParseFailureTracker: JsonParseFailureTracker,
    private val pipelineContextTracker: PipelineContextTracker,
    private val configRevisionService: ConfigRevisionService,
    private val objectMapper: ObjectMapper,
    private val settings: BotSettings,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val SYSTEM_ACTOR = "system"
    }

    fun begin(): String? {
        pipelineLlmUsageTracker.begin()
        jsonParseFailureTracker.begin()
        val revisionId = runCatching { configRevisionService.currentRevisionId() }
            .onFailure { LOGGER.warn(it) { "Failed to capture configuration revision" } }
            .getOrNull()
        pipelineContextTracker.begin(revisionId)
        return revisionId
    }

    fun currentConfigRevisionId(): String? = pipelineContextTracker.configRevisionId()

    fun recordContextSource(uri: String) = pipelineContextTracker.recordSource(uri)

    fun discard() {
        pipelineLlmUsageTracker.drain()
        jsonParseFailureTracker.drain()
        pipelineContextTracker.discard()
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
            val jsonFailures = jsonParseFailureTracker.drain()
            val contextManifest = pipelineContextTracker.drain()
            pipelineRunRepository.save(
                PipelineRunEntity(
                    pipelineKey = pipelineKey,
                    roomTarget = trigger.roomTarget,
                    triggerMessageId = trigger.id,
                    triggerSenderLogin = trigger.senderLogin,
                    triggerText = trigger.messageText.take(settings.bot().limits.traceTriggerTextMax),
                    outcome = outcome,
                    outcomeDetail = outcomeDetail,
                    outboundMessageId = outboundMessageId,
                    stagesJson = objectMapper.writeValueAsString(stages),
                    totalTokens = llmUsage.sumOf { it.tokens },
                    llmUsageJson = objectMapper.writeValueAsString(llmUsage),
                    jsonParseFailureCount = jsonFailures.size,
                    jsonParseFailuresJson = objectMapper.writeValueAsString(jsonFailures),
                    configRevisionId = contextManifest.configRevisionId,
                    contextManifestJson = pipelineContextTracker.serialize(contextManifest),
                ),
            )
        }.onFailure { LOGGER.warn(it) { "Failed to record pipeline trace for ${trigger.roomTarget}" } }
    }

    fun recordSummary(
        roomTarget: String,
        trigger: SummaryRefreshTrigger,
        outcome: String,
        stages: List<PipelineStage>,
        outcomeDetail: String? = null,
    ): String? = runCatching {
        val llmUsage = pipelineLlmUsageTracker.drain()
        val jsonFailures = jsonParseFailureTracker.drain()
        val contextManifest = pipelineContextTracker.drain()
        val triggerMessage = (trigger as? SummaryRefreshTrigger.Message)?.message
        val saved = pipelineRunRepository.save(
            PipelineRunEntity(
                pipelineKey = PipelineKeys.SUMMARY,
                roomTarget = roomTarget,
                triggerMessageId = triggerMessage?.id,
                triggerSenderLogin = triggerMessage?.senderLogin ?: SYSTEM_ACTOR,
                triggerText = triggerMessage?.messageText?.take(settings.bot().limits.traceTriggerTextMax)
                    ?: "Summary refresh · ${trigger.label}",
                outcome = outcome,
                outcomeDetail = outcomeDetail,
                outboundMessageId = null,
                stagesJson = objectMapper.writeValueAsString(stages),
                totalTokens = llmUsage.sumOf { it.tokens },
                llmUsageJson = objectMapper.writeValueAsString(llmUsage),
                jsonParseFailureCount = jsonFailures.size,
                jsonParseFailuresJson = objectMapper.writeValueAsString(jsonFailures),
                configRevisionId = contextManifest.configRevisionId,
                contextManifestJson = pipelineContextTracker.serialize(contextManifest),
            ),
        )
        saved.id
    }.onFailure { LOGGER.warn(it) { "Failed to record summary trace for $roomTarget" } }.getOrNull()
}
