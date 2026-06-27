package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
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
    private val interestClassifier: InterestClassifier,
    private val replyGenerator: ReplyGenerator,
    private val roomSummaryService: RoomSummaryService,
    private val cooldownTracker: BotCooldownTracker,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @PostConstruct
    fun logConfig() {
        LOGGER.info { "Bot config: enabled=${botProperties.enabled}, rooms=${botProperties.rooms}, name=${botProperties.persona.name}, debounce=${botProperties.decision.debounceSeconds}s, cooldown=${botProperties.decision.cooldownSeconds}s" }
    }

    /** Called (async, after commit) when new messages are ingested. Schedules a debounced evaluation. */
    @Async
    fun onMessagesStored(storedMessages: List<ChatMessageEntity>) {
        if (!botProperties.enabled || storedMessages.isEmpty()) return

        val allowedRooms = botProperties.rooms.toSet()
        storedMessages
            .filter { it.roomTarget in allowedRooms }
            .filterNot { it.senderLogin.equals(botProperties.persona.name, ignoreCase = true) }
            .map { it.roomTarget }
            .distinct()
            .forEach { roomTarget ->
                botDebouncer.schedule(roomTarget) { evaluateRoom(roomTarget) }
            }
    }

    private fun evaluateRoom(roomTarget: String) {
        runCatching {
            val trigger = latestNonBotMessage(roomTarget) ?: return

            // Keep long-term context fresh (cheap tier, only when stale).
            runCatching { roomSummaryService.refreshIfStale(roomTarget) }
                .onFailure { LOGGER.debug(it) { "Summary refresh skipped for $roomTarget" } }

            val shouldReply = when (heuristicGate.evaluate(trigger)) {
                GateDecision.IGNORE -> false
                GateDecision.REPLY_NOW -> cooldownTracker.canReply(roomTarget)
                GateDecision.MAYBE -> decideAmbiguous(roomTarget)
            }
            if (!shouldReply) return

            val reply = replyGenerator.generate(roomTarget, trigger) ?: return

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

    private fun decideAmbiguous(roomTarget: String): Boolean {
        if (!cooldownTracker.canReply(roomTarget)) return false
        val verdict = interestClassifier.shouldRespond(roomTarget)
        if (verdict.respond) return true
        // Occasionally chime in unprompted to feel alive.
        return Random.nextDouble() < botProperties.decision.spontaneousProbability
    }
}
