package org.taonity.sinairllmbot.chat.repository

import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatEventRepository : JpaRepository<ChatEventEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean

    /** Most recent presence/status events in a room, newest first. */
    fun findByRoomTargetOrderByEventTimeDesc(roomTarget: String, pageable: Pageable): List<ChatEventEntity>
}
