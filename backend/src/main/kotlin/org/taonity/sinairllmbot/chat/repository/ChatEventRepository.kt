package org.taonity.sinairllmbot.chat.repository

import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ChatEventRepository : JpaRepository<ChatEventEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean

    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<ChatEventEntity>

    fun findByRoomTargetOrderByEventTimeDesc(roomTarget: String, pageable: Pageable): List<ChatEventEntity>

    @Query(
        """
        SELECT e FROM ChatEventEntity e
        WHERE ((:field = 'all' OR :field = 'memberName') AND LOWER(e.memberName) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'status') AND LOWER(e.status) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'eventData') AND LOWER(COALESCE(e.eventData, '')) LIKE LOWER(CONCAT('%', :q, '%')))
           OR (:field = 'all' AND LOWER(e.roomTarget) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
    )
    fun search(q: String, field: String, pageable: Pageable): Page<ChatEventEntity>

    @Query(
        """
        SELECT COUNT(e) FROM ChatEventEntity e
        WHERE e.eventTime > :eventTime OR (e.eventTime = :eventTime AND e.id > :id)
        """,
    )
    fun countOrderedBefore(eventTime: Instant, id: String): Long

    @Query(
        """
        SELECT COUNT(e) FROM ChatEventEntity e
        WHERE e.eventTime < :eventTime OR (e.eventTime = :eventTime AND e.id < :id)
        """,
    )
    fun countOrderedBeforeAsc(eventTime: Instant, id: String): Long

    @Modifying
    fun deleteByEventTimeBefore(cutoff: Instant): Int
}
