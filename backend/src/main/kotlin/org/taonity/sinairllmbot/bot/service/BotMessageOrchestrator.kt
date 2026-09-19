package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.pipeline.PipelineAlternative
import org.taonity.sinairllmbot.bot.pipeline.PipelineField
import org.taonity.sinairllmbot.bot.pipeline.PipelineKeys
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.common.config.AppProperties
import java.time.Instant

@Service
class BotMessageOrchestrator(
    private val settings: BotSettings,
    private val appProperties: AppProperties,
    private val botDebouncer: BotDebouncer,
    private val commandGate: CommandGate,
    private val messageTriageService: MessageTriageService,
    private val replyGenerator: ReplyGenerator,
    private val roomSummaryService: RoomSummaryService,
    private val cooldownTracker: BotCooldownTracker,
    private val mutedRoomRegistry: MutedRoomRegistry,
    private val botSleepService: BotSleepService,
    private val botTypingService: BotTypingService,
    private val roomProcessingGuard: RoomProcessingGuard,
    private val pipelineTraceService: PipelineTraceService,
    private val pendingMessages: PendingBotMessages,
) {
    private val botProperties get() = settings.bot()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val FAILURE_MESSAGE = "Что-то пошло не так, пока я готовил ответ."
    }


    @Async
    fun onMessagesStored(storedMessages: List<ChatMessageEntity>) {
        if (!botProperties.enabled || storedMessages.isEmpty()) return

        val allowedRooms = settings.botRooms().toSet()
        val eligible = storedMessages
            .filter { it.roomTarget in allowedRooms }
            .filterNot { botSleepService.isAsleep(it.roomTarget) }
            .filterNot { it.senderLogin.equals(botProperties.persona.name, ignoreCase = true) }
            .filter { it.sourceOutboundMessageId == null }
        pendingMessages.enqueue(eligible, botProperties.decision.debounceSeconds)
        eligible
            .map { it.roomTarget }
            .distinct()
            .filterNot { botSleepService.isAsleep(it) }
            .forEach { roomTarget ->
                botDebouncer.schedule(roomTarget) {
                    roomProcessingGuard.runExclusive(roomTarget) { drainRoom(roomTarget) }
                }
            }
    }

    @Scheduled(fixedDelay = 5000)
    fun resumePending() {
        if (!botProperties.enabled) return
        pendingMessages.dueRooms()
            .filter { it in settings.botRooms() && !botSleepService.isAsleep(it) }
            .forEach { room ->
                botDebouncer.schedule(room, 0) {
                    roomProcessingGuard.runExclusive(room) { drainRoom(room) }
                }
            }
    }

    @Scheduled(fixedDelay = 60000)
    fun refreshSummaries() {
        if (!botProperties.enabled) return
        settings.botRooms().forEach { room ->
            botDebouncer.schedule("summary:$room", 0) {
                runCatching { roomSummaryService.refreshIfStale(room, SummaryRefreshTrigger.Job("background")) }
                    .onFailure { LOGGER.warn { "Background summary refresh failed: ${it.javaClass.simpleName}" } }
            }
        }
    }

    private fun drainRoom(roomTarget: String) {
        repeat(20) {
            if (pendingMessages.next(roomTarget) == null || botSleepService.isAsleep(roomTarget)) return
            evaluateRoom(roomTarget)
        }
    }

    internal fun evaluateRoom(roomTarget: String) {
        val trigger = runCatching { pendingMessages.next(roomTarget) }
            .onFailure { LOGGER.warn(it) { "Failed to find the trigger message for room $roomTarget" } }
            .getOrNull() ?: return
        val stages = mutableListOf<PipelineStage>()
        var generationStarted = false

        try {

            pipelineTraceService.begin()

            val commandDecision = commandGate.evaluate(trigger)

            if (commandDecision == CommandDecision.STOP_BOT) {
                mutedRoomRegistry.mute(roomTarget)
                pendingMessages.finish(trigger)
                LOGGER.info { "Bot muted in $roomTarget by @${trigger.senderLogin}" }
                stages += PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "mute command")
                pipelineTraceService.record(
                    PipelineKeys.REPLY, trigger, PipelineOutcome.MUTE_COMMAND, stages,
                    outcomeDetail = "muted by @${trigger.senderLogin}",
                )
                return
            }
            if (commandDecision == CommandDecision.START_BOT) {
                val wasRemoved = mutedRoomRegistry.unmute(roomTarget)
                pendingMessages.finish(trigger)
                if (wasRemoved) {
                    LOGGER.info { "Bot un-muted in $roomTarget by @${trigger.senderLogin}" }
                }
                stages += PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "un-mute command")
                pipelineTraceService.record(
                    PipelineKeys.REPLY, trigger, PipelineOutcome.UNMUTE_COMMAND, stages,
                    outcomeDetail = "un-muted by @${trigger.senderLogin}",
                )
                return
            }
            stages += PipelineStage("command", "Command gate", PipelineStageStatus.PASS, "no command")

            if (mutedRoomRegistry.isMuted(roomTarget)) {
                pendingMessages.finish(trigger)
                stages += PipelineStage("mute", "Mute check", PipelineStageStatus.STOP, "room muted")
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.MUTED, stages)
                return
            }
            val triage = messageTriageService.assess(roomTarget, trigger)
            stages += PipelineStage(
                key = "triage",
                label = "Triage",
                status = PipelineStageStatus.OK,
                summary = "respond=${triage.respond} · ${triage.loggableCategory}",
                fields = listOf(
                    PipelineField("respond", triage.respond.toString()),
                    PipelineField("category", triage.loggableCategory),
                ),
            )

            val shouldReply = triage.respond
            val driver = when {
                triage.respond -> "triage"
                else -> "none"
            }
            stages += PipelineStage(
                key = "decision",
                label = "Reply decision",
                status = if (shouldReply) PipelineStageStatus.OK else PipelineStageStatus.STOP,
                summary = if (shouldReply) "reply (driver=$driver)" else "stay silent",
                fields = listOf(
                    PipelineField("driver", driver),
                    PipelineField("reply", shouldReply.toString()),
                ),
            )
            LOGGER.info {
                "Gate decision for $roomTarget @${trigger.senderLogin}: reply=$shouldReply " +
                    "driver=$driver (respond=${triage.respond}, category=${triage.loggableCategory})"
            }
            if (!shouldReply) {
                pendingMessages.finish(trigger)
                pipelineTraceService.record(
                    PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages, outcomeDetail = "driver=$driver",
                )
                return
            }

            val requested = triage.loggableCategory in setOf("direct_address", "indirect_address")
            val graceUntil = trigger.receivedAt.plusSeconds(botProperties.decision.openQuestionDelaySeconds)
            if (!requested && Instant.now().isBefore(graceUntil)) {
                pendingMessages.defer(trigger, graceUntil)
                stages += PipelineStage("grace", "Conversation grace period", PipelineStageStatus.STOP, "waiting for human replies")
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages, outcomeDetail = "deferred contribution")
                return
            }
            if (!cooldownTracker.canReply(roomTarget, requested = requested)) {
                pendingMessages.defer(trigger, Instant.now().plusSeconds(30))
                stages += PipelineStage("cooldown", "Cooldown", PipelineStageStatus.STOP, "request retained")
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.COOLDOWN, stages)
                return
            }
            stages += PipelineStage("cooldown", "Cooldown", PipelineStageStatus.PASS, "ready")

            generationStarted = true
            val contextVersion = pendingMessages.latestHumanMessageId(roomTarget, botProperties.persona.name)
            botTypingService.markTyping(roomTarget)
            val generation = replyGenerator.generateTraced(
                roomTarget = roomTarget,
                trigger = trigger,
                completedStages = stages.toList(),
                configRevisionId = pipelineTraceService.currentConfigRevisionId(),
            )
            stages += generationStage(generation)

            val superseded = contextVersion != pendingMessages.latestHumanMessageId(roomTarget, botProperties.persona.name) &&
                runCatching { !messageTriageService.assess(roomTarget, trigger, verifyStillNeeded = true).respond }.getOrDefault(false)
            if (superseded) {
                botTypingService.clearTyping(roomTarget)
                pendingMessages.finish(trigger)
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages, outcomeDetail = "superseded during generation")
                return
            }

            if (generation.suppressed) {
                botTypingService.clearTyping(roomTarget)
                pendingMessages.finish(trigger)
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages, outcomeDetail = "no remaining contribution")
                return
            }

            val reply = generation.reply ?: run {
                handleFailure(trigger, stages, "generation produced no reply")
                return
            }

            val saved = pendingMessages.reply(trigger, reply)
            botTypingService.clearTyping(roomTarget)
            runCatching { cooldownTracker.recordReply(roomTarget) }
                .onFailure { LOGGER.warn(it) { "Failed to record reply cooldown for $roomTarget" } }
            LOGGER.info { "Bot queued reply in $roomTarget to @${trigger.senderLogin}" }
            pipelineTraceService.record(
                PipelineKeys.REPLY, trigger, PipelineOutcome.REPLIED, stages, outboundMessageId = saved.id,
            )
        } catch (exception: Exception) {
            if (!generationStarted) {
                pendingMessages.defer(trigger, Instant.now().plusSeconds(30))
                stages += PipelineStage("triage_error", "Assessment deferred", PipelineStageStatus.STOP, exception.javaClass.simpleName)
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.FAILED, stages, outcomeDetail = "assessment failed; request retained")
                return
            }
            val detail = exception.message?.takeIf { it.isNotBlank() }
                ?: exception.javaClass.simpleName
            stages += PipelineStage("error", "Pipeline error", PipelineStageStatus.STOP, detail)
            handleFailure(trigger, stages, detail, exception)
        }
    }

    private fun handleFailure(
        trigger: ChatMessageEntity,
        stages: List<PipelineStage>,
        detail: String,
        exception: Exception? = null,
    ) {
        botTypingService.clearTyping(trigger.roomTarget)
        exception?.let { LOGGER.warn(it) { "Bot pipeline failed for room ${trigger.roomTarget}" } }
        val pipelineId = pipelineTraceService.record(
            PipelineKeys.REPLY,
            trigger,
            PipelineOutcome.FAILED,
            stages,
            outcomeDetail = detail,
        )
        val message = pipelineId?.let { "$FAILURE_MESSAGE Пайплайн: ${pipelineUrl(it)}" } ?: FAILURE_MESSAGE
        runCatching {
            pendingMessages.reply(trigger, message)
            cooldownTracker.recordReply(trigger.roomTarget)
        }.onFailure { LOGGER.warn(it) { "Failed to queue fallback reply in ${trigger.roomTarget}" } }
    }

    private fun pipelineUrl(pipelineId: String): String =
        UriComponentsBuilder.fromUriString(appProperties.defaultSuccessUrl)
            .replaceQueryParam("pipeline", pipelineId)
            .build()
            .encode()
            .toUriString()

    private fun generationStage(generation: ReplyGeneration): PipelineStage {
        val alternatives = generation.candidates.map { candidate ->
            val fields = buildList {
                candidate.overall?.let { add(PipelineField("overall", it.toString())) }
                candidate.fit?.let { add(PipelineField("fit", it.toString())) }
                candidate.persona?.let { add(PipelineField("persona", it.toString())) }
                candidate.risk?.let { add(PipelineField("risk", it.toString())) }
            }
            PipelineAlternative(text = candidate.text, chosen = candidate.chosen, fields = fields)
        }
        val fields = buildList {
            add(PipelineField("candidates", generation.candidates.size.toString()))
            if (generation.criticUsed) add(PipelineField("critic", "used"))
            if (generation.repaired) add(PipelineField("repaired", "true"))
            generation.criticFeedback?.let { add(PipelineField("feedback", it)) }
        }
        val summary = buildString {
            append(generation.candidates.size)
            append(if (generation.candidates.size == 1) " candidate" else " candidates")
            generation.chosenIndex?.let { append(" · chose #").append(it) }
            if (generation.repaired) append(" · repaired")
        }
        return PipelineStage(
            key = "generate",
            label = "Reply generation",
            status = if (generation.reply == null) PipelineStageStatus.STOP else PipelineStageStatus.OK,
            summary = summary,
            fields = fields,
            alternatives = alternatives,
        )
    }

}
