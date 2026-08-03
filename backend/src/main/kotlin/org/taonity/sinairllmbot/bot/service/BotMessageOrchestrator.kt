package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineAlternative
import org.taonity.sinairllmbot.bot.pipeline.PipelineField
import org.taonity.sinairllmbot.bot.pipeline.PipelineKeys
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import kotlin.random.Random

@Service
class BotMessageOrchestrator(
    private val settings: BotSettings,
    private val botDebouncer: BotDebouncer,
    private val commandGate: CommandGate,
    private val messageTriageService: MessageTriageService,
    private val replyGenerator: ReplyGenerator,
    private val roomSummaryService: RoomSummaryService,
    private val cooldownTracker: BotCooldownTracker,
    private val mutedRoomRegistry: MutedRoomRegistry,
    private val botSleepService: BotSleepService,
    private val botTypingService: BotTypingService,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val pipelineTraceService: PipelineTraceService,
) {
    private val botProperties get() = settings.bot()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }


    @Async
    fun onMessagesStored(storedMessages: List<ChatMessageEntity>) {
        if (!botProperties.enabled || storedMessages.isEmpty()) return

        val allowedRooms = settings.botRooms().toSet()
        storedMessages
            .filter { it.roomTarget in allowedRooms }
            .filterNot { it.senderLogin.equals(botProperties.persona.name, ignoreCase = true) }
            .map { it.roomTarget }
            .distinct()
            .filterNot { botSleepService.isAsleep(it) }
            .forEach { roomTarget ->
                botDebouncer.schedule(roomTarget) { evaluateRoom(roomTarget) }
            }
    }

    private fun evaluateRoom(roomTarget: String) {
        runCatching {
            val trigger = latestNonBotMessage(roomTarget) ?: return

            runCatching { roomSummaryService.refreshIfStale(roomTarget, SummaryRefreshTrigger.Message(trigger)) }
                .onFailure { LOGGER.debug(it) { "Summary refresh skipped for $roomTarget" } }

            pipelineTraceService.begin()

            val stages = mutableListOf<PipelineStage>()

            val commandDecision = commandGate.evaluate(trigger)

            if (commandDecision == CommandDecision.STOP_BOT) {
                mutedRoomRegistry.mute(roomTarget)
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
                stages += PipelineStage("mute", "Mute check", PipelineStageStatus.STOP, "room muted")
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.MUTED, stages)
                return
            }
            if (!cooldownTracker.canReply(roomTarget)) {
                LOGGER.debug { "Skip $roomTarget: on cooldown (@${trigger.senderLogin})" }
                stages += PipelineStage("cooldown", "Cooldown", PipelineStageStatus.STOP, "on cooldown")
                pipelineTraceService.record(PipelineKeys.REPLY, trigger, PipelineOutcome.COOLDOWN, stages)
                return
            }
            stages += PipelineStage("cooldown", "Cooldown", PipelineStageStatus.PASS, "ready")


            val triage = messageTriageService.assess(roomTarget, trigger.messageText)
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

            val spontaneous = !triage.respond &&
                Random.nextDouble() < botProperties.decision.spontaneousProbability
            val shouldReply = triage.respond || spontaneous
            val driver = when {
                triage.respond -> "triage"
                spontaneous -> "spontaneous"
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
                pipelineTraceService.record(
                    PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages, outcomeDetail = "driver=$driver",
                )
                return
            }

            botTypingService.markTyping(roomTarget)
            val generation = runCatching {
                replyGenerator.generateTraced(
                    roomTarget = roomTarget,
                    trigger = trigger,
                    completedStages = stages.toList(),
                    configRevisionId = pipelineTraceService.currentConfigRevisionId(),
                )
            }.onFailure {
                botTypingService.clearTyping(roomTarget)
                throw it
            }.getOrThrow()
            stages += generationStage(generation)

            val reply = generation.reply ?: run {
                botTypingService.clearTyping(roomTarget)
                pipelineTraceService.record(
                    PipelineKeys.REPLY, trigger, PipelineOutcome.SILENT, stages,
                    outcomeDetail = "generation produced no reply",
                )
                return
            }

            val saved = outboundMessageRepository.save(
                OutboundMessageEntity(
                    roomTarget = roomTarget,
                    messageText = reply,
                    replyToExternalId = trigger.dedupKey
                        .takeIf { it.startsWith("ext:") }
                        ?.removePrefix("ext:"),
                ),
            )
            botTypingService.clearTyping(roomTarget)
            cooldownTracker.recordReply(roomTarget)
            LOGGER.info { "Bot queued reply in $roomTarget to @${trigger.senderLogin}" }
            pipelineTraceService.record(
                PipelineKeys.REPLY, trigger, PipelineOutcome.REPLIED, stages, outboundMessageId = saved.id,
            )
        }.onFailure {
            pipelineTraceService.discard()
            LOGGER.warn(it) { "Bot pipeline failed for room $roomTarget" }
        }
    }

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
            status = PipelineStageStatus.OK,
            summary = summary,
            fields = fields,
            alternatives = alternatives,
        )
    }

    private fun latestNonBotMessage(roomTarget: String): ChatMessageEntity? =
        chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, 5))
            .firstOrNull { !it.senderLogin.equals(botProperties.persona.name, ignoreCase = true) }
}
