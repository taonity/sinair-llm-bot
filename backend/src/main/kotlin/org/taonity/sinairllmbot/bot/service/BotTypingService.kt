package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which rooms the bot is actively "typing" in, so the collector can reflect a live typing
 * indicator in chat from the moment the bot decides to reply until the reply is actually sent.
 *
 * A room shows as typing while either:
 *  - a reply is being composed for it (marked around the reply-generation call), capped by a TTL
 *    so a stalled/failed generation can't leave the indicator stuck on; or
 *  - it still has an unsent (PENDING) outbound reply queued — bridging the gap between generation
 *    finishing and the collector claiming and delivering the message.
 */
@Service
class BotTypingService(
    private val botProperties: BotProperties,
    private val outboundMessageRepository: OutboundMessageRepository,
) {
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
