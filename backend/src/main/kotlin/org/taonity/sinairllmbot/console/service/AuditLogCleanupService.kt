package org.taonity.sinairllmbot.console.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.console.repository.AuditLogRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Enforces the 2-week retention policy for audit logs. */
@Service
class AuditLogCleanupService(
    private val auditLogRepository: AuditLogRepository,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val RETENTION_DAYS = 14L
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    fun cleanupOldAuditLogs() {
        val cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
        val removed = auditLogRepository.deleteByOccurredAtBefore(cutoff)
        LOGGER.info { "Audit log retention cleanup: removed $removed entries older than $cutoff" }
    }
}
