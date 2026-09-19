package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.PendingBotMessageEntity
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant
import org.springframework.data.domain.PageRequest

@Service
class PendingBotMessages(
    private val pendingRepository: PendingBotMessageRepository,
    private val messageRepository: ChatMessageRepository,
    private val outboundRepository: OutboundMessageRepository,
) {
    @Transactional
    fun enqueue(messages: List<ChatMessageEntity>, delaySeconds: Long) {
        val now = Instant.now()
        messages.forEach { message ->
            val messageId = requireNotNull(message.id)
            if (!pendingRepository.existsById(messageId)) {
                pendingRepository.save(PendingBotMessageEntity(messageId, message.roomTarget, now.plusSeconds(delaySeconds), message.receivedAt))
            }
        }
    }

    fun dueRooms(): List<String> = pendingRepository.dueRooms(Instant.now())

    fun latestHumanMessageId(room: String, botName: String): String? = messageRepository
        .findByRoomTargetOrderBySentAtDesc(room, PageRequest.of(0, 25))
        .firstOrNull { it.sourceOutboundMessageId == null && !it.senderLogin.equals(botName, ignoreCase = true) }?.id

    fun next(room: String): ChatMessageEntity? = pendingRepository
        .findFirstByRoomTargetAndAvailableAtLessThanEqualOrderByCreatedAtAsc(room, Instant.now())
        ?.let { messageRepository.findById(it.messageId).orElse(null) }

    @Transactional
    fun defer(message: ChatMessageEntity, until: Instant) {
        pendingRepository.findById(message.id!!).orElse(null)?.let {
            it.availableAt = until
            pendingRepository.save(it)
        }
    }

    fun finish(message: ChatMessageEntity) = pendingRepository.deleteById(message.id!!)

    @Transactional
    fun reply(message: ChatMessageEntity, text: String): OutboundMessageEntity {
        val saved = outboundRepository.save(OutboundMessageEntity(
            roomTarget = message.roomTarget,
            messageText = text,
            replyToExternalId = message.dedupKey.takeIf { it.startsWith("ext:") }?.removePrefix("ext:"),
            triggerMessageId = message.id,
        ))
        pendingRepository.deleteById(message.id!!)
        return saved
    }
}