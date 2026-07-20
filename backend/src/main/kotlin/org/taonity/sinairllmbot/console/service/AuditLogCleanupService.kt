package org.taonity.sinairllmbot.console.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.console.repository.AuditLogRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuditLogCleanupService(
    private val auditLogRepository: AuditLogRepository,
    private val settings: BotSettings,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun cleanupOldAuditLogs() {
        val cutoff = Instant.now().minus(settings.retention().audit.retentionDays, ChronoUnit.DAYS)
        val removed = auditLogRepository.deleteByOccurredAtBefore(cutoff)
        LOGGER.info { "Audit log retention cleanup: removed $removed entries older than $cutoff" }
    }
}
