package org.taonity.sinairllmbot.chat.repository

import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean

    fun findByRoomTarget(roomTarget: String, pageable: Pageable): Page<ChatMessageEntity>

    fun findByRoomTargetOrderBySentAtDesc(roomTarget: String, pageable: Pageable): List<ChatMessageEntity>

    fun existsBySourceOutboundMessageId(sourceOutboundMessageId: String): Boolean

    fun findBySourceOutboundMessageId(sourceOutboundMessageId: String): ChatMessageEntity?

    fun countByRoomTarget(roomTarget: String): Long

    @Query("select count(message) from ChatMessageEntity message where message.roomTarget = :room and (message.receivedAt > :receivedAt or (message.receivedAt = :receivedAt and message.id > :id))")
    fun countAfterWatermark(room: String, receivedAt: Instant, id: String): Long

    @Query("select message from ChatMessageEntity message where message.roomTarget = :room and (message.receivedAt > :receivedAt or (message.receivedAt = :receivedAt and message.id > :id)) order by message.receivedAt, message.id")
    fun findAfterWatermark(room: String, receivedAt: Instant, id: String, pageable: Pageable): List<ChatMessageEntity>

    @Query(
        """
        SELECT m FROM ChatMessageEntity m
        WHERE ((:field = 'all' OR :field = 'messageText') AND LOWER(m.messageText) LIKE LOWER(CONCAT('%', :q, '%')))
           OR ((:field = 'all' OR :field = 'senderLogin') AND LOWER(m.senderLogin) LIKE LOWER(CONCAT('%', :q, '%')))
           OR (:field = 'all' AND LOWER(m.roomTarget) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
    )
    fun search(q: String, field: String, pageable: Pageable): Page<ChatMessageEntity>

    @Query(
        """
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.sentAt > :sentAt OR (m.sentAt = :sentAt AND m.id > :id)
        """,
    )
    fun countOrderedBefore(sentAt: Instant, id: String): Long

    @Query(
        """
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.sentAt < :sentAt OR (m.sentAt = :sentAt AND m.id < :id)
        """,
    )
    fun countOrderedBeforeAsc(sentAt: Instant, id: String): Long

    @Query("SELECT DISTINCT m.roomTarget FROM ChatMessageEntity m WHERE m.sentAt < :cutoff")
    fun findDistinctRoomTargetBySentAtBefore(cutoff: Instant): List<String>

    @Modifying
    fun deleteBySentAtBefore(cutoff: Instant): Int
}
