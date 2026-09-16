package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class BotCooldownTracker(
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()
    private val replyTimestamps = ConcurrentHashMap<String, MutableList<Instant>>()

    fun canReply(roomTarget: String, now: Instant = Instant.now(), requested: Boolean = false): Boolean {
        val decision = botProperties.decision
        val window = Duration.ofMinutes(decision.windowMinutes)
        val timestamps = replyTimestamps.computeIfAbsent(roomTarget) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeIf { Duration.between(it, now) > window }
            val last = timestamps.maxOrNull()
            val cooldown = if (requested) decision.requestedCooldownSeconds else decision.cooldownSeconds
            if (last != null && Duration.between(last, now).seconds < cooldown) {
                return false
            }
            return timestamps.size < if (requested) decision.maxRequestedRepliesPerWindow else decision.maxRepliesPerWindow
        }
    }

    fun recordReply(roomTarget: String, now: Instant = Instant.now()) {
        val timestamps = replyTimestamps.computeIfAbsent(roomTarget) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.add(now)
        }
    }
}
