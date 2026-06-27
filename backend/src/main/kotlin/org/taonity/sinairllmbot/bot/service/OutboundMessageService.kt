package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import java.time.Instant

/**
 * Delivery queue API for the collector: claim PENDING replies (marking them CLAIMED so they are
 * not handed out twice) and acknowledge them as SENT once delivered to the chat.
 */
@Service
class OutboundMessageService(
    private val outboundMessageRepository: OutboundMessageRepository,
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
        LOGGER.info { "Acknowledged ${claimed.size} outbound messages as SENT" }
        return claimed.size
    }

    private fun org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity.toDto() = OutboundMessageDto(
        id = id!!,
        roomTarget = roomTarget,
        messageText = messageText,
        replyToExternalId = replyToExternalId,
    )
}
