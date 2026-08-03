package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.entity.RoomBotStateEntity
import org.taonity.sinairllmbot.bot.repository.RoomBotStateRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MutedRoomRegistry(
    private val roomBotStateRepository: RoomBotStateRepository,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val mutedRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @PostConstruct
    fun loadFromDb() {
        roomBotStateRepository.findByMutedTrue().forEach { mutedRooms.add(it.roomTarget) }
        if (mutedRooms.isNotEmpty()) {
            LOGGER.info { "Restored muted rooms from DB: $mutedRooms" }
        }
    }

    fun mute(roomTarget: String): Boolean {
        val added = mutedRooms.add(roomTarget)
        if (added) persistMuted(roomTarget, true)
        return added
    }

    fun unmute(roomTarget: String): Boolean {
        val removed = mutedRooms.remove(roomTarget)
        if (removed) persistMuted(roomTarget, false)
        return removed
    }

    fun isMuted(roomTarget: String): Boolean = roomTarget in mutedRooms

    private fun persistMuted(roomTarget: String, muted: Boolean) {
        val entity = roomBotStateRepository.findById(roomTarget)
            .orElseGet { RoomBotStateEntity(roomTarget = roomTarget) }
        entity.muted = muted
        entity.updatedAt = Instant.now()
        roomBotStateRepository.save(entity)
    }
}
