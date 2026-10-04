package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.metrics.BotMetrics
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Duration
import java.time.Instant

@Service
class OutboundMessageService(
    private val outboundMessageRepository: OutboundMessageRepository,
    private val botMetrics: BotMetrics,
    private val chatMessageRepository: ChatMessageRepository,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun claimPending(roomTarget: String?, limit: Int): List<OutboundMessageDto> {
        val pageable = PageRequest.of(0, limit.coerceIn(1, 50))
        val pending = if (roomTarget.isNullOrBlank()) {
            outboundMessageRepository.findByStatusOrderByCreatedAtAsc(OutboundStatus.PENDING, pageable)
        } else {
            outboundMessageRepository.findByRoomTargetAndStatusOrderByCreatedAtAsc(
                roomTarget, OutboundStatus.PENDING, pageable,
            )
        }
        val now = Instant.now()
        pending.forEach {
            it.status = OutboundStatus.CLAIMED
            it.claimedAt = now
        }
        outboundMessageRepository.saveAll(pending)
        if (pending.isNotEmpty()) {
            val scope = if (roomTarget.isNullOrBlank()) "all rooms" else roomTarget
            LOGGER.info { "Claimed ${pending.size} outbound messages ($scope) as CLAIMED" }
        }
        return pending.map { it.toDto() }
    }

    @Transactional
    fun acknowledge(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val claimed = outboundMessageRepository.findByIdInAndStatus(ids, OutboundStatus.CLAIMED)
        val now = Instant.now()
        claimed.forEach {
            it.status = OutboundStatus.SENT
            it.sentAt = now
        }
        outboundMessageRepository.saveAll(claimed)
        if (claimed.isNotEmpty()) {
            val count = claimed.size
            val triggers = chatMessageRepository.findAllById(claimed.mapNotNull { it.triggerMessageId }.distinct())
                .associateBy { it.id }
            val latencies = claimed.mapNotNull { message ->
                triggers[message.triggerMessageId]?.let { Duration.between(it.receivedAt, now) }
            }
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    botMetrics.recordAcknowledgedReplies(count)
                    latencies.forEach(botMetrics::recordReplyLatency)
                }
            })
            LOGGER.info { "Acknowledged ${claimed.size} outbound messages as SENT" }
        }
        return claimed.size
    }

    private fun org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity.toDto() = OutboundMessageDto(
        id = id!!,
        roomTarget = roomTarget,
        messageText = messageText,
        replyToExternalId = replyToExternalId,
    )
}
