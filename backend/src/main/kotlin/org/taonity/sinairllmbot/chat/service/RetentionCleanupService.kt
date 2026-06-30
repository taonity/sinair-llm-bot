package org.taonity.sinairllmbot.chat.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.service.RoomSummaryService
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RetentionCleanupService(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatEventRepository: ChatEventRepository,
    private val outboundMessageRepository: OutboundMessageRepository,
    private val roomSummaryService: RoomSummaryService,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val RETENTION_DAYS = 7L
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupOldRecords() {
        val cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
        LOGGER.info { "Retention cleanup: removing records older than $cutoff" }

        val affectedRooms = chatMessageRepository.findDistinctRoomTargetBySentAtBefore(cutoff)
        for (room in affectedRooms) {
            roomSummaryService.forceRefresh(room)
        }

        val messages = chatMessageRepository.deleteBySentAtBefore(cutoff)
        val events = chatEventRepository.deleteByEventTimeBefore(cutoff)
        val outbound = outboundMessageRepository.deleteByCreatedAtBefore(cutoff)

        LOGGER.info { "Retention cleanup complete: messages=$messages, events=$events, outbound=$outbound" }
    }
}
