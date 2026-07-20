package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory per-room rate limiting so the bot doesn't spam: a minimum gap between replies
 * plus a cap on replies within a sliding window.
 */
@Component
class BotCooldownTracker(
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()
    private val replyTimestamps = ConcurrentHashMap<String, MutableList<Instant>>()

    fun canReply(roomTarget: String, now: Instant = Instant.now()): Boolean {
        val decision = botProperties.decision
        val window = Duration.ofMinutes(decision.windowMinutes)
        val timestamps = replyTimestamps.computeIfAbsent(roomTarget) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.removeIf { Duration.between(it, now) > window }
            val last = timestamps.maxOrNull()
            if (last != null && Duration.between(last, now).seconds < decision.cooldownSeconds) {
                return false
            }
            return timestamps.size < decision.maxRepliesPerWindow
        }
    }

    fun recordReply(roomTarget: String, now: Instant = Instant.now()) {
        val timestamps = replyTimestamps.computeIfAbsent(roomTarget) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.add(now)
        }
    }
}
