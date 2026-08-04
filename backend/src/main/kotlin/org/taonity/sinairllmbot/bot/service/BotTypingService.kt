package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class BotTypingService(
    private val outboundMessageRepository: OutboundMessageRepository,
) {
    private val activeGenerations = ConcurrentHashMap<String, AtomicInteger>()

    fun markTyping(roomTarget: String) {
        activeGenerations.compute(roomTarget) { _, count ->
            (count ?: AtomicInteger()).apply { incrementAndGet() }
        }
    }

    fun clearTyping(roomTarget: String) {
        activeGenerations.computeIfPresent(roomTarget) { _, count ->
            count.takeIf { it.decrementAndGet() > 0 }
        }
    }

    fun typingRooms(): List<String> {
        val queued = outboundMessageRepository.findDistinctRoomTargetsByStatus(OutboundStatus.PENDING)
        return (activeGenerations.keys + queued).distinct()
    }
}
