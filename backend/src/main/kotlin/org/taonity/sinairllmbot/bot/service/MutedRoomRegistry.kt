package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.entity.RoomBotStateEntity
import org.taonity.sinairllmbot.bot.repository.RoomBotStateRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks rooms where the bot has been muted via the `!stop` command (and un-muted via `!start`).
 *
 * Shared between the message pipeline (which honours the mute when deciding to reply) and the
 * presence service (a muted room reports the bot as offline).
 *
 * The set is an in-memory cache backed by [RoomBotStateRepository]; it is loaded on startup and
 * written through on every change so the mute state survives a backend restart.
 */
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

    /** Returns true if the room was previously muted. */
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
