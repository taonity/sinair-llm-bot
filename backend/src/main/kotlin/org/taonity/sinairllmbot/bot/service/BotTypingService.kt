package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class BotTypingService(
    private val settings: BotSettings,
    private val outboundMessageRepository: OutboundMessageRepository,
) {
    private val botProperties get() = settings.bot()
    private val typingUntil = ConcurrentHashMap<String, Instant>()

    fun markTyping(roomTarget: String) {
        typingUntil[roomTarget] = Instant.now().plusSeconds(botProperties.typing.ttlSeconds)
    }

    fun clearTyping(roomTarget: String) {
        typingUntil.remove(roomTarget)
    }

    fun typingRooms(): List<String> {
        val now = Instant.now()
        typingUntil.entries.removeIf { it.value.isBefore(now) }
        val queued = outboundMessageRepository.findDistinctRoomTargetsByStatus(OutboundStatus.PENDING)
        return (typingUntil.keys + queued).distinct()
    }
}
