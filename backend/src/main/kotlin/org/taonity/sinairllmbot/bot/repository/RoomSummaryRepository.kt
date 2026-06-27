package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity

@Repository
interface RoomSummaryRepository : JpaRepository<RoomSummaryEntity, String> {
    fun findByRoomTarget(roomTarget: String): RoomSummaryEntity?
}
