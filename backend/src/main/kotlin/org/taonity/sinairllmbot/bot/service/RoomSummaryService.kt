package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.bot.entity.RoomSummaryHistoryEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineField
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus
import org.taonity.sinairllmbot.bot.repository.RoomSummaryHistoryRepository
import org.taonity.sinairllmbot.bot.repository.RoomSummaryRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant

/**
 * Maintains a rolling, compressed summary per room so the bot has long-term context without
 * paying to resend full history on every reply. Refreshed with the cheap "gate" tier, and only
 * once enough new messages have accumulated.
 */
@Service
class RoomSummaryService(
    private val roomSummaryRepository: RoomSummaryRepository,
    private val roomSummaryHistoryRepository: RoomSummaryHistoryRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val contextBuilder: ConversationContextBuilder,
    private val llmClient: LlmClient,
    private val settings: BotSettings,
    private val pipelineLlmUsageTracker: PipelineLlmUsageTracker,
    private val pipelineTraceService: PipelineTraceService,
) {
    private val botProperties get() = settings.bot()
    private val llmProperties get() = settings.llm()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional(readOnly = true)
    fun currentSummary(roomTarget: String): String =
        roomSummaryRepository.findByRoomTarget(roomTarget)?.summary.orEmpty()

    @Transactional
    fun refreshIfStale(roomTarget: String, trigger: SummaryRefreshTrigger) {
        refreshInternal(roomTarget, force = false, trigger = trigger)
    }

    @Transactional
    fun forceRefresh(roomTarget: String, trigger: SummaryRefreshTrigger) {
        refreshInternal(roomTarget, force = true, trigger = trigger)
    }

    private fun refreshInternal(roomTarget: String, force: Boolean, trigger: SummaryRefreshTrigger) {
        val existing = roomSummaryRepository.findByRoomTarget(roomTarget)
        val totalMessages = chatMessageRepository.countByRoomTarget(roomTarget).toInt()
        val sinceLast = totalMessages - (existing?.messageCount ?: 0)
        if (!force && existing != null && sinceLast < botProperties.context.summaryRefreshEveryMessages) {
            return
        }
        if (sinceLast == 0) return

        val transcript = contextBuilder.recentTranscript(roomTarget, limit = 60)
        if (transcript.isBlank()) return

        val previousSummary = existing?.summary
        // Trace the refresh as its own "summary" pipeline run so the console shows the same detail as
        // a reply run (its LLM call, with request/response payloads). begin()/record run on this
        // thread; the reply pipeline (BotMessageOrchestrator) starts its own tracking afterwards.
        pipelineLlmUsageTracker.begin()
        val newSummary = generateSummary(previousSummary, transcript)
        if (newSummary == null) {
            pipelineTraceService.recordSummary(
                roomTarget = roomTarget,
                trigger = trigger,
                outcome = PipelineOutcome.SUMMARY_FAILED,
                stages = listOf(
                    summaryStage(
                        status = PipelineStageStatus.STOP,
                        summaryText = "LLM produced no summary",
                        trigger = trigger,
                        totalMessages = totalMessages,
                        sinceLast = sinceLast,
                        previousSummary = previousSummary,
                        newSummary = null,
                        transcript = transcript,
                        force = force,
                    ),
                ),
                outcomeDetail = "summary generation failed",
            )
            return
        }

        val current = if (existing == null) {
            RoomSummaryEntity(
                roomTarget = roomTarget,
                summary = newSummary,
                messageCount = totalMessages,
                updatedAt = Instant.now(),
            )
        } else {
            archivePreviousVersion(existing)
            existing.summary = newSummary
            existing.messageCount = totalMessages
            existing.updatedAt = Instant.now()
            existing
        }
        roomSummaryRepository.save(current)
        val runId = pipelineTraceService.recordSummary(
            roomTarget = roomTarget,
            trigger = trigger,
            outcome = PipelineOutcome.SUMMARY_REFRESHED,
            stages = listOf(
                summaryStage(
                    status = PipelineStageStatus.OK,
                    summaryText = "${previousSummary?.length ?: 0} → ${newSummary.length} chars",
                    trigger = trigger,
                    totalMessages = totalMessages,
                    sinceLast = sinceLast,
                    previousSummary = previousSummary,
                    newSummary = newSummary,
                    transcript = transcript,
                    force = force,
                ),
            ),
        )
        // Link the summary to the pipeline run holding its source transcript, so the console can show
        // the messages behind it — until retention purges that run after 7 days (the summary stays).
        if (runId != null) {
            current.pipelineRunId = runId
            roomSummaryRepository.save(current)
        }
        LOGGER.info { "Refreshed room summary for $roomTarget ($totalMessages msgs)" }
    }

    /** Archive the summary that is about to be overwritten, then prune to the latest few versions. */
    private fun archivePreviousVersion(existing: RoomSummaryEntity) {
        roomSummaryHistoryRepository.save(
            RoomSummaryHistoryEntity(
                roomTarget = existing.roomTarget,
                summary = existing.summary,
                messageCount = existing.messageCount,
                createdAt = existing.updatedAt,
                pipelineRunId = existing.pipelineRunId,
            ),
        )
        val versions = roomSummaryHistoryRepository.findByRoomTargetOrderByCreatedAtDesc(existing.roomTarget)
        val maxHistoryVersions = botProperties.limits.summaryHistoryVersions
        if (versions.size > maxHistoryVersions) {
            roomSummaryHistoryRepository.deleteAll(versions.drop(maxHistoryVersions))
        }
    }

    private fun summaryStage(
        status: PipelineStageStatus,
        summaryText: String,
        trigger: SummaryRefreshTrigger,
        totalMessages: Int,
        sinceLast: Int,
        previousSummary: String?,
        newSummary: String?,
        transcript: String,
        force: Boolean,
    ): PipelineStage = PipelineStage(
        key = "summary",
        label = "Summary refresh",
        status = status,
        summary = summaryText,
        fields = listOf(
            PipelineField("source", trigger.label),
            PipelineField("tier", llmProperties.gateTier),
            PipelineField("messages", totalMessages.toString()),
            PipelineField("newMessages", sinceLast.toString()),
            PipelineField("prevChars", (previousSummary?.length ?: 0).toString()),
            PipelineField("newChars", (newSummary?.length ?: 0).toString()),
            PipelineField("transcriptChars", transcript.length.toString()),
            PipelineField("forced", force.toString()),
        ),
    )

    private fun generateSummary(previousSummary: String?, transcript: String): String? {
        val maxChars = botProperties.context.maxSummaryChars
        val instruction = buildString {
            append("You maintain a running summary of a ")
            append(botProperties.persona.language)
            append(" group chat. ")
            append("Update the summary using the previous summary and the new transcript. ")
            append("Capture durable, recurring context: long-running topics and debates, who tends ")
            append("to argue which side, stable facts about the regulars and their interests. ")
            append("Drop one-off exchanges and threads that have clearly wrapped up — especially ")
            append("transient chatter about fixing bugs, testing features, tweaking settings or ")
            append("day-to-day troubleshooting; keep such an item only if it is still an active, ")
            append("ongoing thread. Prefer what will still matter next week over what was resolved ")
            append("today. Be thorough and information-rich: aim to use most of the available space, ")
            append("landing close to $maxChars characters, without padding or repetition. Stay ")
            append("strictly under $maxChars characters and always finish your final sentence — never ")
            append("stop mid-thought. Write the summary in ")
            append(botProperties.persona.language)
            append(". Output only the summary text.")
        }
        val userContent = buildString {
            if (!previousSummary.isNullOrBlank()) {
                append("PREVIOUS SUMMARY:\n").append(previousSummary).append("\n\n")
            }
            append("NEW TRANSCRIPT:\n").append(transcript)
        }
        val result = llmClient.complete(
            tierName = llmProperties.gateTier,
            messages = listOf(ChatMessage.system(instruction), ChatMessage.user(userContent)),
            maxTokensOverride = botProperties.context.summaryMaxTokens,
        ) ?: return null
        val content = result.content.trim()
        if (content.length <= maxChars) return content
        LOGGER.warn { "Summary exceeded $maxChars chars (${content.length}); truncating" }
        return content.take(maxChars).trimEnd() + " […]"
    }
}
