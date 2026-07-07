package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import java.time.Instant

@Repository
interface OutboundMessageRepository : JpaRepository<OutboundMessageEntity, String> {
    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<OutboundMessageEntity>

    @Query(
        """
        SELECT m FROM OutboundMessageEntity m
        WHERE ((:field = 'all' OR :field = 'messageText') AND LOWER(m.messageText) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'status') AND LOWER(string(m.status)) LIKE LOWER(CONCAT('%', :q, '%')))
           OR (:field = 'all' AND LOWER(m.roomTarget) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
    )
    fun search(q: String, field: String, pageable: Pageable): Page<OutboundMessageEntity>

    @Query(
        """
        SELECT COUNT(m) FROM OutboundMessageEntity m
        WHERE m.createdAt > :createdAt OR (m.createdAt = :createdAt AND m.id > :id)
        """,
    )
    fun countOrderedBefore(createdAt: Instant, id: String): Long

    @Query(
        """
        SELECT COUNT(m) FROM OutboundMessageEntity m
        WHERE m.createdAt < :createdAt OR (m.createdAt = :createdAt AND m.id < :id)
        """,
    )
    fun countOrderedBeforeAsc(createdAt: Instant, id: String): Long

    fun findByRoomTargetAndStatusOrderByCreatedAtAsc(
        roomTarget: String,
        status: OutboundStatus,
        pageable: Pageable,
    ): List<OutboundMessageEntity>

    fun findByStatusOrderByCreatedAtAsc(
        status: OutboundStatus,
        pageable: Pageable,
    ): List<OutboundMessageEntity>

    fun findByIdInAndStatus(ids: Collection<String>, status: OutboundStatus): List<OutboundMessageEntity>

    @Query("SELECT DISTINCT m.roomTarget FROM OutboundMessageEntity m WHERE m.status = :status")
    fun findDistinctRoomTargetsByStatus(status: OutboundStatus): List<String>

    @Modifying
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
