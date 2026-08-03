package org.taonity.sinairllmbot.chat.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.taonity.sinairllmbot.bot.service.BotMessageOrchestrator
import org.taonity.sinairllmbot.bot.service.BotSleepService
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.chat.dto.ChatEventDto
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import org.taonity.sinairllmbot.chat.dto.IngestResponse
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.entity.IgnoredMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.chat.repository.IgnoredMessageRepository
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.taonity.sinairllmbot.config.BotSettings
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.time.Instant
import java.time.Duration

@Service
class ChatIngestService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val ignoredMessageRepository: IgnoredMessageRepository,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val settings: BotSettings,
    private val botMessageOrchestrator: BotMessageOrchestrator,
    private val botSleepService: BotSleepService
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val OUTBOUND_ECHO_WINDOW_SECONDS = 300L
    }

    @Transactional
    fun ingest(request: IngestRequest): IngestResponse {
        var messagesStored = 0
        var messagesDuplicate = 0
        var messagesIgnored = 0
        var eventsStored = 0
        var eventsDuplicate = 0
        val storedMessages = mutableListOf<ChatMessageEntity>()

        for (msg in request.messages) {
            val dedupKey = computeMessageDedupKey(msg)
            // History-burst replays are recorded but never drive commands or bot replies.
            if (!msg.historical) {
                // Detect commands before the skip check so `!wake` can reach us while asleep.
                botSleepService.applyCommand(msg.roomTarget, msg.messageText, msg.senderLogin)
                if (botSleepService.isAsleep(msg.roomTarget)) {
                    // Tombstone the id so the same message replayed after a restart/rejoin stays dropped.
                    if (!ignoredMessageRepository.existsByDedupKey(dedupKey)) {
                        ignoredMessageRepository.save(
                            IgnoredMessageEntity(dedupKey = dedupKey, roomTarget = msg.roomTarget),
                        )
                    }
                    messagesIgnored++
                    continue
                }
            }
            if (chatMessageRepository.existsByDedupKey(dedupKey) ||
                ignoredMessageRepository.existsByDedupKey(dedupKey)
            ) {
                messagesDuplicate++
                continue
            }
            val sentAt = Instant.ofEpochSecond(msg.sentAt)
            val outboundSource = reconcileOutboundEcho(msg, sentAt)
            val saved = chatMessageRepository.save(
                ChatMessageEntity(
                    dedupKey = dedupKey,
                    roomTarget = msg.roomTarget,
                    senderMemberId = msg.senderMemberId,
                    senderUserId = msg.senderUserId,
                    senderLogin = msg.senderLogin,
                    senderColor = msg.senderColor,
                    messageText = msg.messageText,
                    messageStyle = msg.messageStyle,
                    recipientMemberId = msg.recipientMemberId,
                    sentAt = sentAt,
                    receivedAt = Instant.now(),
                    sourceOutboundMessageId = outboundSource,
                    sourceOutboundMatch = outboundSource?.let { "ECHO_TIME_TEXT" },
                )
            )
            // Only live messages feed the bot; historical replays must not trigger a reaction.
            if (!msg.historical) {
                storedMessages.add(saved)
            }
            messagesStored++
        }

        for (event in request.events) {
            val dedupKey = computeEventDedupKey(event)
            if (chatEventRepository.existsByDedupKey(dedupKey)) {
                eventsDuplicate++
                continue
            }
            chatEventRepository.save(
                ChatEventEntity(
                    dedupKey = dedupKey,
                    roomTarget = event.roomTarget,
                    memberId = event.memberId,
                    userId = event.userId,
                    memberName = event.memberName,
                    memberColor = event.memberColor,
                    status = event.status,
                    eventData = event.eventData,
                    isGirl = event.isGirl,
                    isModer = event.isModer,
                    isOwner = event.isOwner,
                    eventTime = Instant.ofEpochSecond(event.eventTime),
                    receivedAt = Instant.now()
                )
            )
            eventsStored++
        }

        LOGGER.debug { "Ingest complete: $messagesStored messages stored, $messagesDuplicate duplicates skipped, $messagesIgnored ignored (asleep), $eventsStored events stored, $eventsDuplicate duplicates skipped" }

        // Run the bot pipeline only after the transaction commits, so the async worker sees the rows.
        if (storedMessages.isNotEmpty()) {
            val toProcess = storedMessages.toList()
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    botMessageOrchestrator.onMessagesStored(toProcess)
                }
            })
        }

        return IngestResponse(messagesStored, messagesDuplicate, eventsStored, eventsDuplicate)
    }

    private fun computeMessageDedupKey(msg: ChatMessageDto): String {
        if (!msg.externalId.isNullOrBlank()) {
            return "ext:${msg.externalId}"
        }
        val raw = "${msg.roomTarget}|${msg.senderMemberId}|${msg.messageText}|${msg.sentAt}"
        return "hash:${sha256(raw)}"
    }

    private fun reconcileOutboundEcho(msg: ChatMessageDto, sentAt: Instant): String? {
        if (!msg.senderLogin.equals(settings.bot().persona.name, ignoreCase = true)) return null
        val matched = outboundMessageRepository.findByRoomTargetAndMessageTextAndStatusInOrderByCreatedAtDesc(
            roomTarget = msg.roomTarget,
            messageText = msg.messageText,
            statuses = listOf(OutboundStatus.CLAIMED, OutboundStatus.SENT),
            pageable = PageRequest.of(0, 10),
        ).asSequence()
            .filter { it.id != null && !chatMessageRepository.existsBySourceOutboundMessageId(it.id!!) }
            .map { it to kotlin.math.abs(Duration.between(it.sentAt ?: it.claimedAt ?: it.createdAt, sentAt).seconds) }
            .filter { (_, seconds) -> seconds <= OUTBOUND_ECHO_WINDOW_SECONDS }
            .minByOrNull { (_, seconds) -> seconds }
            ?.first
            ?.id
        if (matched != null) {
            LOGGER.debug {
                "ECHO_TIME_TEXT matched outbound $matched to room ${msg.roomTarget} " +
                    "sender=${msg.senderLogin} text=\"${msg.messageText.take(80)}\""
            }
        }
        return matched
    }

    private fun computeEventDedupKey(event: ChatEventDto): String {
        val raw = "${event.roomTarget}|${event.memberId}|${event.status}|${event.eventTime}|${event.eventData.orEmpty()}"
        return "hash:${sha256(raw)}"
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(40)
    }
}
