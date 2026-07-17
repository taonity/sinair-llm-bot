package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import java.time.Instant

@Repository
interface PipelineRunRepository : JpaRepository<PipelineRunEntity, String> {
    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<PipelineRunEntity>

    @Query(
        """
        SELECT r FROM PipelineRunEntity r
        WHERE ((:field = 'all' OR :field = 'triggerText') AND LOWER(r.triggerText) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'triggerSenderLogin') AND LOWER(r.triggerSenderLogin) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'outcome') AND LOWER(r.outcome) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'pipelineKey') AND LOWER(r.pipelineKey) LIKE LOWER(CONCAT('%', :q, '%')))
           OR (:field = 'all' AND LOWER(r.roomTarget) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
    )
    fun search(q: String, field: String, pageable: Pageable): Page<PipelineRunEntity>

    @Query(
        """
        SELECT COUNT(r) FROM PipelineRunEntity r
        WHERE r.createdAt > :createdAt OR (r.createdAt = :createdAt AND r.id > :id)
        """,
    )
    fun countOrderedBefore(createdAt: Instant, id: String): Long

    @Query(
        """
        SELECT COUNT(r) FROM PipelineRunEntity r
        WHERE r.createdAt < :createdAt OR (r.createdAt = :createdAt AND r.id < :id)
        """,
    )
    fun countOrderedBeforeAsc(createdAt: Instant, id: String): Long

    @Modifying
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
