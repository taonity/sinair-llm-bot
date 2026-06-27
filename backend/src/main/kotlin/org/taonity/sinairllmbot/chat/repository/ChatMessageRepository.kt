package org.taonity.sinairllmbot.chat.repository

import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean

    /** Most recent messages in a room, newest first. Caller reverses for chronological order. */
    fun findByRoomTargetOrderBySentAtDesc(roomTarget: String, pageable: Pageable): List<ChatMessageEntity>

    fun countByRoomTarget(roomTarget: String): Long
}
