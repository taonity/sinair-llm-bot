package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.PendingBotMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.entity.BotResponseState
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.lang.management.ManagementFactory
import java.time.Instant
import org.springframework.data.domain.PageRequest

@Service
class PendingBotMessages(
    private val pendingRepository: PendingBotMessageRepository,
    private val messageRepository: ChatMessageRepository,
    private val outboundRepository: OutboundMessageRepository,
) {
    val startedAt: Instant = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().startTime)

    fun isCurrentRun(message: ChatMessageEntity): Boolean =
        !message.sentAt.isBefore(startedAt) && !message.receivedAt.isBefore(startedAt)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun discardPreviousRun() {
        pendingRepository.findByCreatedAtBefore(startedAt).forEach { pending ->
            messageRepository.findById(pending.messageId).orElse(null)?.let { discardBeforeRestart(it) }
            pendingRepository.deleteById(pending.messageId)
        }
        outboundRepository.findByStatusInAndCreatedAtBefore(listOf(OutboundStatus.PENDING, OutboundStatus.CLAIMED), startedAt)
            .forEach { discardOutbound(it) }
    }

    fun discardOutbound(outbound: OutboundMessageEntity) {
        outbound.status = OutboundStatus.DISCARDED
        outboundRepository.save(outbound)
        outbound.triggerMessageId?.let { messageRepository.findById(it).orElse(null) }?.let { discardBeforeRestart(it) }
    }

    private fun discardBeforeRestart(message: ChatMessageEntity) {
        updateResponse(message, "DISCARDED", "RESTART", "Message predates this backend run; no reply will be sent.")
    }

    @Transactional
    fun enqueue(messages: List<ChatMessageEntity>, delaySeconds: Long) {
        val now = Instant.now()
        messages.forEach { message ->
            if (!isCurrentRun(message)) {
                discardBeforeRestart(message)
                return@forEach
            }
            val messageId = requireNotNull(message.id)
            if (!pendingRepository.existsById(messageId)) {
                pendingRepository.save(PendingBotMessageEntity(messageId, message.roomTarget, now.plusSeconds(delaySeconds), message.receivedAt))
                updateResponse(message, "PENDING", "ASSESSMENT_PENDING")
            }
        }
    }

    fun dueRooms(): List<String> = pendingRepository.dueRooms(Instant.now())

    fun latestHumanMessageId(room: String, botName: String): String? = messageRepository
        .findByRoomTargetOrderBySentAtDesc(room, PageRequest.of(0, 25))
        .firstOrNull { it.sourceOutboundMessageId == null && !it.senderLogin.equals(botName, ignoreCase = true) }?.id

    @Transactional
    fun next(room: String): ChatMessageEntity? {
        while (true) {
            val pending = pendingRepository.findFirstByRoomTargetAndAvailableAtLessThanEqualOrderByCreatedAtAsc(room, Instant.now())
                ?: return null
            val message = messageRepository.findById(pending.messageId).orElse(null)
            if (message != null && isCurrentRun(message)) return message
            message?.let { discardBeforeRestart(it) }
            pendingRepository.deleteById(pending.messageId)
        }
    }

    @Transactional
    fun defer(message: ChatMessageEntity, until: Instant, reason: String = "COOLDOWN", detail: String? = null, category: String? = null) {
        pendingRepository.findById(message.id!!).orElse(null)?.let {
            it.availableAt = until
            pendingRepository.save(it)
            updateResponse(message, "DEFERRED", reason, detail, category, until)
        }
    }

    @Transactional
    fun finish(message: ChatMessageEntity, reason: String = "GATE_DECLINED", detail: String? = null, category: String? = null) {
        updateResponse(message, "DISCARDED", reason, detail, category)
        pendingRepository.deleteById(message.id!!)
    }

    @Transactional
    fun fail(message: ChatMessageEntity, reason: String, detail: String) {
        updateResponse(message, "FAILED", reason, detail)
        pendingRepository.deleteById(message.id!!)
    }

    @Transactional
    fun delivered(outbound: OutboundMessageEntity) {
        val message = outbound.triggerMessageId?.let { messageRepository.findById(it).orElse(null) } ?: return
        if (message.botResponse?.reason == "GENERATION_FAILED") return
        updateResponse(message, "REPLIED", "DELIVERED", outboundMessageId = outbound.id)
    }

    @Transactional
    fun reply(message: ChatMessageEntity, text: String, failure: Boolean = false): OutboundMessageEntity {
        check(isCurrentRun(message)) { "Cannot reply to a message from before this backend run" }
        val saved = outboundRepository.save(OutboundMessageEntity(
            roomTarget = message.roomTarget,
            messageText = text,
            replyToExternalId = message.dedupKey.takeIf { it.startsWith("ext:") }?.removePrefix("ext:"),
            triggerMessageId = message.id,
        ))
        updateResponse(message, if (failure) "FAILED" else "REPLY_QUEUED", if (failure) "GENERATION_FAILED" else "REPLY_GENERATED", outboundMessageId = saved.id)
        pendingRepository.deleteById(message.id!!)
        return saved
    }

    private fun updateResponse(
        message: ChatMessageEntity,
        status: String,
        reason: String,
        detail: String? = null,
        category: String? = null,
        nextAttemptAt: Instant? = null,
        outboundMessageId: String? = null,
    ) {
        val stored = messageRepository.findById(message.id!!).orElse(null) ?: return
        val previous = stored.botResponse
        stored.botResponse = BotResponseState(
            status = status, reason = reason, detail = detail,
            category = category ?: previous?.category,
            deferredAt = previous?.deferredAt ?: Instant.now().takeIf { status == "DEFERRED" },
            nextAttemptAt = nextAttemptAt,
            outboundMessageId = outboundMessageId ?: previous?.outboundMessageId,
        )
        messageRepository.save(stored)
    }
}