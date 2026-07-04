package org.taonity.sinairllmbot.bot.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.taonity.sinairllmbot.bot.entity.RoomBotStateEntity

@Repository
interface RoomBotStateRepository : JpaRepository<RoomBotStateEntity, String> {
    fun findByMutedTrue(): List<RoomBotStateEntity>

    fun findByAsleepTrue(): List<RoomBotStateEntity>
}
