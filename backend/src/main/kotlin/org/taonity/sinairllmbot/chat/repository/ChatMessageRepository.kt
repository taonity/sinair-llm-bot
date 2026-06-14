package org.taonity.sinairllmbot.chat.repository

import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, String> {
    fun existsByDedupKey(dedupKey: String): Boolean
}
