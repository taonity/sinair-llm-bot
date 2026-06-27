package org.taonity.sinairllmbot.bot.service

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.BotProperties
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * Per-room quiet-period debounce: when a room gets new messages, schedule a single evaluation
 * after [BotProperties.Decision.debounceSeconds]. Each new burst reschedules, so a fast back-and-forth
 * between humans is judged once (after it settles) instead of on every ingest flush.
 */
@Component
class BotDebouncer(
    private val botProperties: BotProperties,
) {
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("bot-debounce-")
        setWaitForTasksToCompleteOnShutdown(false)
        initialize()
    }

    private val pending = ConcurrentHashMap<String, ScheduledFuture<*>>()

    fun schedule(roomTarget: String, action: Runnable) {
        synchronized(pending) {
            pending.remove(roomTarget)?.cancel(false)
            val runAt = Instant.now().plusSeconds(botProperties.decision.debounceSeconds)
            val future = scheduler.schedule({
                pending.remove(roomTarget)
                action.run()
            }, runAt)
            pending[roomTarget] = future
        }
    }
}
