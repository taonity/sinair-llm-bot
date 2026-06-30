package org.taonity.sinairllmbot.console.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.console.entity.AuditLogEntity
import java.time.Instant

@Repository
interface AuditLogRepository : JpaRepository<AuditLogEntity, String> {
    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): Page<AuditLogEntity>

    @Modifying
    fun deleteByOccurredAtBefore(cutoff: Instant): Int
}
