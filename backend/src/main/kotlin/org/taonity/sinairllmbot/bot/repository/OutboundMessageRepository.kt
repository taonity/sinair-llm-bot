package org.taonity.sinairllmbot.bot.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import java.time.Instant

@Repository
interface OutboundMessageRepository : JpaRepository<OutboundMessageEntity, String> {
    fun findByStatusInAndCreatedAtBefore(statuses: Collection<OutboundStatus>, cutoff: Instant): List<OutboundMessageEntity>

    fun countByRoomTargetInAndStatusIn(rooms: Collection<String>, statuses: Collection<OutboundStatus>): Long

    @Query("select min(outbound.createdAt) from OutboundMessageEntity outbound where outbound.roomTarget in :rooms and outbound.status in :statuses")
    fun oldestCreatedAt(rooms: Collection<String>, statuses: Collection<OutboundStatus>): Instant?

    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<OutboundMessageEntity>

    @Query(
        """
        SELECT outbound FROM OutboundMessageEntity outbound
        WHERE outbound.roomTarget = :roomTarget AND outbound.triggerMessageId IS NOT NULL
                    AND outbound.status <> org.taonity.sinairllmbot.bot.entity.OutboundStatus.DISCARDED
          AND outbound.createdAt >= :since
          AND NOT EXISTS (
              SELECT echo.id FROM ChatMessageEntity echo
              WHERE echo.roomTarget = outbound.roomTarget AND echo.sourceOutboundMessageId = outbound.id
          )
        ORDER BY outbound.createdAt DESC, outbound.id DESC
        """,
    )
    fun findRecentUnechoedReplies(roomTarget: String, since: Instant, pageable: Pageable): List<OutboundMessageEntity>

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdInAndStatus(ids: Collection<String>, status: OutboundStatus): List<OutboundMessageEntity>

    fun findByRoomTargetAndMessageTextAndStatusInOrderByCreatedAtDesc(
        roomTarget: String,
        messageText: String,
        statuses: Collection<OutboundStatus>,
        pageable: Pageable,
    ): List<OutboundMessageEntity>

    @Query("SELECT DISTINCT m.roomTarget FROM OutboundMessageEntity m WHERE m.status = :status")
    fun findDistinctRoomTargetsByStatus(status: OutboundStatus): List<String>

    @Modifying
    fun deleteByCreatedAtBefore(cutoff: Instant): Int
}
