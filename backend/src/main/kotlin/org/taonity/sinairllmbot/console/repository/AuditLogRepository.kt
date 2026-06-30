package org.taonity.sinairllmbot.console.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.console.entity.AuditLogEntity
import java.time.Instant

@Repository
interface AuditLogRepository : JpaRepository<AuditLogEntity, String> {
    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): Page<AuditLogEntity>

    @Query(
        """
        SELECT a FROM AuditLogEntity a
        WHERE ((:field = 'all' OR :field = 'action') AND LOWER(string(a.action)) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'targetType') AND LOWER(a.targetType) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'targetId') AND LOWER(COALESCE(a.targetId, '')) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'actorEmail') AND LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY a.occurredAt DESC
        """,
    )
    fun search(q: String, field: String, pageable: Pageable): Page<AuditLogEntity>

    @Modifying
    fun deleteByOccurredAtBefore(cutoff: Instant): Int
}
