package org.taonity.sinairllmbot.chat.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.taonity.sinairllmbot.chat.dto.ChatEventDto
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import org.taonity.sinairllmbot.chat.dto.IngestResponse
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant

@Service
class ChatIngestService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun ingest(request: IngestRequest): IngestResponse {
        var messagesStored = 0
        var messagesDuplicate = 0
        var eventsStored = 0
        var eventsDuplicate = 0

        for (msg in request.messages) {
            val dedupKey = computeMessageDedupKey(msg)
            if (chatMessageRepository.existsByDedupKey(dedupKey)) {
                messagesDuplicate++
                continue
            }
            chatMessageRepository.save(
                ChatMessageEntity(
                    dedupKey = dedupKey,
                    roomTarget = msg.roomTarget,
                    senderMemberId = msg.senderMemberId,
                    senderLogin = msg.senderLogin,
                    senderColor = msg.senderColor,
                    messageText = msg.messageText,
                    messageStyle = msg.messageStyle,
                    recipientMemberId = msg.recipientMemberId,
                    sentAt = Instant.ofEpochSecond(msg.sentAt),
                    receivedAt = Instant.now()
                )
            )
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

        LOGGER.info { "Ingest complete: $messagesStored messages stored, $messagesDuplicate duplicates skipped, $eventsStored events stored, $eventsDuplicate duplicates skipped" }
        return IngestResponse(messagesStored, messagesDuplicate, eventsStored, eventsDuplicate)
    }

    private fun computeMessageDedupKey(msg: ChatMessageDto): String {
        if (!msg.externalId.isNullOrBlank()) {
            return "ext:${msg.externalId}"
        }
        val raw = "${msg.roomTarget}|${msg.senderMemberId}|${msg.messageText}|${msg.sentAt}"
        return "hash:${sha256(raw)}"
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
