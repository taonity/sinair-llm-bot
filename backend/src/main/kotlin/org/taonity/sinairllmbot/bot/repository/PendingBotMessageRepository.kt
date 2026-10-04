package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.taonity.sinairllmbot.bot.entity.PendingBotMessageEntity
import java.time.Instant

interface PendingBotMessageRepository : JpaRepository<PendingBotMessageEntity, String> {
    fun countByRoomTargetIn(rooms: Collection<String>): Long

    @Query("select min(pending.createdAt) from PendingBotMessageEntity pending where pending.roomTarget in :rooms")
    fun oldestCreatedAt(rooms: Collection<String>): Instant?

    fun findFirstByRoomTargetAndAvailableAtLessThanEqualOrderByCreatedAtAsc(roomTarget: String, now: Instant): PendingBotMessageEntity?

    @Query("select distinct pending.roomTarget from PendingBotMessageEntity pending where pending.availableAt <= :now")
    fun dueRooms(now: Instant): List<String>
}