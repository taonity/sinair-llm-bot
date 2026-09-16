package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.taonity.sinairllmbot.bot.entity.PendingBotMessageEntity
import java.time.Instant

interface PendingBotMessageRepository : JpaRepository<PendingBotMessageEntity, String> {
    fun findFirstByRoomTargetAndAvailableAtLessThanEqualOrderByCreatedAtAsc(roomTarget: String, now: Instant): PendingBotMessageEntity?

    @Query("select distinct pending.roomTarget from PendingBotMessageEntity pending where pending.availableAt <= :now")
    fun dueRooms(now: Instant): List<String>
}