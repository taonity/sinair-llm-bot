package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.config.BotProperties
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks per-room `!sleep` / `!wake` state. Sleep is scoped per room because one bot instance
 * serves one room; several instances share this backend for different rooms.
 */
@Service
class BotSleepService(
    private val botProperties: BotProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val asleepRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun isAsleep(roomTarget: String): Boolean = roomTarget in asleepRooms

    fun applyCommand(roomTarget: String, messageText: String, senderLogin: String) {
        val text = messageText.trim()
        when {
            text.equals(botProperties.persona.sleepCommand, ignoreCase = true) -> {
                if (asleepRooms.add(roomTarget)) {
                    LOGGER.info { "Bot put to sleep (inactive) in $roomTarget by @$senderLogin" }
                }
            }
            text.equals(botProperties.persona.wakeCommand, ignoreCase = true) -> {
                if (asleepRooms.remove(roomTarget)) {
                    LOGGER.info { "Bot woken up (active) in $roomTarget by @$senderLogin" }
                }
            }
        }
    }
}
