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
 *   command gate -> LLM triage (should respond? fresh info?) -> reply generator -> persist outbound.
 * The collector later picks up PENDING outbound messages and sends them to the chat.
 */
@Service
class BotMessageOrchestrator(
    private val botProperties: BotProperties,
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

            val commandDecision = commandGate.evaluate(trigger)

            if (commandDecision == CommandDecision.STOP_BOT) {
                mutedRoomRegistry.mute(roomTarget)
                LOGGER.info { "Bot muted in $roomTarget by @${trigger.senderLogin}" }
                return
            }
            if (commandDecision == CommandDecision.START_BOT) {
                val wasRemoved = mutedRoomRegistry.unmute(roomTarget)
                if (wasRemoved) {
                    LOGGER.info { "Bot un-muted in $roomTarget by @${trigger.senderLogin}" }
                }
                return
            }

            if (mutedRoomRegistry.isMuted(roomTarget)) return
            if (!cooldownTracker.canReply(roomTarget)) {
                LOGGER.debug { "Skip $roomTarget: on cooldown (@${trigger.senderLogin})" }
                return
            }

            // The cheap gate-tier LLM is the sole judge of intent: whether the bot is addressed
            // (directly or indirectly) or should correct misinformation, and whether the answer
            // needs fresh info. No keyword/noise heuristics.
            val triage = messageTriageService.assess(roomTarget)
            val spontaneous = !triage.respond &&
                Random.nextDouble() < botProperties.decision.spontaneousProbability
            val shouldReply = triage.respond || spontaneous
            val driver = when {
                triage.respond -> "triage"
                spontaneous -> "spontaneous"
                else -> "none"
            }
            LOGGER.info {
                "Gate decision for $roomTarget @${trigger.senderLogin}: reply=$shouldReply " +
                    "driver=$driver (respond=${triage.respond}, needsFreshInfo=${triage.needsFreshInfo}, " +
                    "needsSearch=${triage.needsSearch}, reason='${triage.reason}')"
            }
            if (!shouldReply) return

            // Decided to reply: show a typing indicator while the LLM composes the answer. The
            // marker is cleared once generation returns; the queued PENDING reply then keeps the
            // indicator up (via BotTypingService) until the collector delivers it.
            botTypingService.markTyping(roomTarget)
            val reply = try {
                replyGenerator.generate(roomTarget, trigger, triage.needsFreshInfo || triage.needsSearch)
            } finally {
                botTypingService.clearTyping(roomTarget)
            } ?: return

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
