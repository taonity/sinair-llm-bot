package org.taonity.sinairllmbot.bot.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RetryExecutorTest {
    @Test
    fun `retries transient exceptions until success`() {
        var calls = 0

        val result = RetryExecutor.execute(
            name = "exception-then-success",
            maxAttempts = 3,
            backoffMillis = 0,
        ) {
            calls++
            if (calls < 3) throw IllegalStateException("temporary failure")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `retries failed tool results until success`() {
        var calls = 0

        val result = RetryExecutor.execute(
            name = "result-then-success",
            maxAttempts = 3,
            backoffMillis = 0,
            shouldRetryResult = { it.startsWith("ERROR:") },
        ) {
            calls++
            if (calls == 1) "ERROR: temporary failure" else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `does not retry non-retryable exception`() {
        var calls = 0

        assertThrows(IllegalArgumentException::class.java) {
            RetryExecutor.execute(
                name = "non-retryable",
                maxAttempts = 3,
                backoffMillis = 0,
                shouldRetryException = { false },
            ) {
                calls++
                throw IllegalArgumentException("bad request")
            }
        }

        assertEquals(1, calls)
    }

    @Test
    fun `returns final failed result after exhausting attempts`() {
        var calls = 0

        val result = RetryExecutor.execute(
            name = "exhausted-results",
            maxAttempts = 3,
            backoffMillis = 0,
            shouldRetryResult = { true },
        ) {
            calls++
            "ERROR: unavailable"
        }

        assertEquals("ERROR: unavailable", result)
        assertEquals(3, calls)
    }
}
