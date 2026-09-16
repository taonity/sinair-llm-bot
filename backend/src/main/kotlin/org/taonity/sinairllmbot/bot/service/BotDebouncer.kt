package org.taonity.sinairllmbot.bot.service

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Executors
import jakarta.annotation.PreDestroy

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
    private val workers = Executors.newFixedThreadPool(4)
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val versions = ConcurrentHashMap<String, Any>()

    fun schedule(roomTarget: String, delaySeconds: Long = botProperties.decision.debounceSeconds, action: Runnable) {
        synchronized(pending) {
            pending.remove(roomTarget)?.cancel(false)
            val version = Any()
            versions[roomTarget] = version
            val runAt = Instant.now().plusSeconds(delaySeconds)
            val future = scheduler.schedule({
                if (versions.remove(roomTarget, version) && running.add(roomTarget)) {
                    workers.execute {
                        try {
                            action.run()
                        } finally {
                            running.remove(roomTarget)
                        }
                    }
                }
            }, runAt)
            pending[roomTarget] = future
        }
    }

    @PreDestroy
    fun close() {
        scheduler.shutdown()
        workers.shutdownNow()
    }
}
