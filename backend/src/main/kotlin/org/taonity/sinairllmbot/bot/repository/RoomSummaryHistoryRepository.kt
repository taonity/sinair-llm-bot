package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.RoomSummaryHistoryEntity

@Repository
interface RoomSummaryHistoryRepository : JpaRepository<RoomSummaryHistoryEntity, String> {
    fun findByRoomTargetOrderByCreatedAtDesc(roomTarget: String): List<RoomSummaryHistoryEntity>
}
