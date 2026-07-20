package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.entity.RoomBotStateEntity
import org.taonity.sinairllmbot.bot.repository.RoomBotStateRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-room `!sleep` / `!wake` state. Sleep is scoped per room because one bot instance
 * serves one room; several instances share this backend for different rooms.
 *
 * The set is an in-memory cache backed by [RoomBotStateRepository]; it is loaded on startup and
 * written through on every change so the state survives a backend restart.
 */
@Service
class BotSleepService(
    private val settings: BotSettings,
    private val roomBotStateRepository: RoomBotStateRepository,
) {
    private val botProperties get() = settings.bot()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val asleepRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @PostConstruct
    fun loadFromDb() {
        roomBotStateRepository.findByAsleepTrue().forEach { asleepRooms.add(it.roomTarget) }
        if (asleepRooms.isNotEmpty()) {
            LOGGER.info { "Restored asleep rooms from DB: $asleepRooms" }
        }
    }

    fun isAsleep(roomTarget: String): Boolean = roomTarget in asleepRooms

    fun applyCommand(roomTarget: String, messageText: String, senderLogin: String) {
        val text = messageText.trim()
        when {
            text.equals(botProperties.persona.sleepCommand, ignoreCase = true) -> {
                if (asleepRooms.add(roomTarget)) {
                    persistAsleep(roomTarget, true)
                    LOGGER.info { "Bot put to sleep (inactive) in $roomTarget by @$senderLogin" }
                }
            }
            text.equals(botProperties.persona.wakeCommand, ignoreCase = true) -> {
                if (asleepRooms.remove(roomTarget)) {
                    persistAsleep(roomTarget, false)
                    LOGGER.info { "Bot woken up (active) in $roomTarget by @$senderLogin" }
                }
            }
        }
    }

    private fun persistAsleep(roomTarget: String, asleep: Boolean) {
        val entity = roomBotStateRepository.findById(roomTarget)
            .orElseGet { RoomBotStateEntity(roomTarget = roomTarget) }
        entity.asleep = asleep
        entity.updatedAt = Instant.now()
        roomBotStateRepository.save(entity)
    }
}
