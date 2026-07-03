package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import kotlin.random.Random

/**
 * Orchestrates the "should I respond, and with what?" pipeline after new messages are ingested.
 *
 * Bursts are debounced per room, so a fast human back-and-forth is judged once after it settles.
 * After the quiet period the latest room state is re-read from the DB and run through:
 *   heuristic gate -> (interest classifier) -> reply generator -> persist outbound (PENDING).
 * The collector later picks up PENDING outbound messages and sends them to the chat.
 */
@Service
class BotMessageOrchestrator(
    private val botProperties: BotProperties,
    private val botDebouncer: BotDebouncer,
    private val heuristicGate: HeuristicGate,
    private val messageTriageService: MessageTriageService,
    private val replyGenerator: ReplyGenerator,
    private val roomSummaryService: RoomSummaryService,
    private val cooldownTracker: BotCooldownTracker,
    private val mutedRoomRegistry: MutedRoomRegistry,
    private val botSleepService: BotSleepService,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }


    @Async
    fun onMessagesStored(storedMessages: List<ChatMessageEntity>) {
        if (!botProperties.enabled || storedMessages.isEmpty()) return

        val allowedRooms = botProperties.rooms.toSet()
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

            runCatching { roomSummaryService.refreshIfStale(roomTarget) }
                .onFailure { LOGGER.debug(it) { "Summary refresh skipped for $roomTarget" } }

            val gateDecision = heuristicGate.evaluate(trigger)

            if (gateDecision == GateDecision.STOP_BOT) {
                mutedRoomRegistry.mute(roomTarget)
                LOGGER.info { "Bot muted in $roomTarget by @${trigger.senderLogin}" }
                return
            }
            if (gateDecision == GateDecision.START_BOT) {
                val wasRemoved = mutedRoomRegistry.unmute(roomTarget)
                if (wasRemoved) {
                    LOGGER.info { "Bot un-muted in $roomTarget by @${trigger.senderLogin}" }
                }
                return
            }

            if (mutedRoomRegistry.isMuted(roomTarget)) return

            if (gateDecision == GateDecision.IGNORE) return
            if (!cooldownTracker.canReply(roomTarget)) return

            // One cheap triage call decides indirect-addressing (respond) and freshness need at once.
            val triage = messageTriageService.assess(roomTarget)

            val shouldReply = when (gateDecision) {
                GateDecision.REPLY_NOW -> true
                GateDecision.MAYBE ->
                    triage.respond || Random.nextDouble() < botProperties.decision.spontaneousProbability
                GateDecision.IGNORE, GateDecision.STOP_BOT, GateDecision.START_BOT -> false
            }
            if (!shouldReply) return

            val reply = replyGenerator.generate(roomTarget, trigger, triage.needsFreshInfo) ?: return

            outboundMessageRepository.save(
                OutboundMessageEntity(
                    roomTarget = roomTarget,
                    messageText = reply,
                    replyToExternalId = trigger.dedupKey
                        .takeIf { it.startsWith("ext:") }
                        ?.removePrefix("ext:"),
                ),
            )
            cooldownTracker.recordReply(roomTarget)
            LOGGER.info { "Bot queued reply in $roomTarget to @${trigger.senderLogin}" }
        }.onFailure { LOGGER.warn(it) { "Bot pipeline failed for room $roomTarget" } }
    }

    private fun latestNonBotMessage(roomTarget: String): ChatMessageEntity? =
        chatMessageRepository
            .findByRoomTargetOrderBySentAtDesc(roomTarget, PageRequest.of(0, 5))
            .firstOrNull { !it.senderLogin.equals(botProperties.persona.name, ignoreCase = true) }
}
