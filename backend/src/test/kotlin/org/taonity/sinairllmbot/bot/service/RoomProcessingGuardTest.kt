package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomProcessingGuardTest {
    private val guard = RoomProcessingGuard()

    @Test
    fun `serializes processing in the same room`() {
        val executor = Executors.newFixedThreadPool(2)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        try {
            val first = executor.submit {
                guard.runExclusive("room") {
                    firstEntered.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS)
                }
            }
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue()

            val second = executor.submit {
                secondAttempted.countDown()
                guard.runExclusive("room") { secondEntered.countDown() }
            }
            assertThat(secondAttempted.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(secondEntered.await(100, TimeUnit.MILLISECONDS)).isFalse()

            releaseFirst.countDown()
            assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }
}