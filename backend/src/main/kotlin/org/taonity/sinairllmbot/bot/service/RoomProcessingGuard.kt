package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class RoomProcessingGuard {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun <T> runExclusive(roomTarget: String, action: () -> T): T =
        locks.computeIfAbsent(roomTarget) { ReentrantLock() }.withLock(action)
}