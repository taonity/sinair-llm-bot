package org.taonity.sinairllmbot.console.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.console.entity.AuditAction
import org.taonity.sinairllmbot.console.entity.AuditLogEntity
import org.taonity.sinairllmbot.console.repository.AuditLogRepository
import org.taonity.sinairllmbot.user.entity.UserEntity

/**
 * Records audit entries for data console changes. Entries never contain the changed data itself —
 * only the action, the affected record's type/id, the actor and the timestamp.
 */
@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun record(action: AuditAction, targetType: String, targetId: String?, actor: UserEntity) {
        auditLogRepository.save(
            AuditLogEntity(
                action = action,
                targetType = targetType,
                targetId = targetId,
                actorGoogleId = actor.googleId,
                actorEmail = actor.email,
            )
        )
        LOGGER.info { "Audit: action=$action targetType=$targetType targetId=$targetId actor=${actor.googleId}" }
    }
}
