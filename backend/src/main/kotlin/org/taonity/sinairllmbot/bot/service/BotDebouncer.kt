package org.taonity.sinairllmbot.bot.service

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class BotDebouncer(
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()
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
