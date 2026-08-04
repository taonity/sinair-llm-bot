package org.taonity.sinairllmbot.bot.client

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Predicate

internal object RetryExecutor {
    fun <T> execute(
        name: String,
        maxAttempts: Int,
        backoffMillis: Long,
        shouldRetryResult: (T) -> Boolean = { false },
        shouldRetryException: (Exception) -> Boolean = { true },
        onRetry: (nextAttempt: Int, cause: String) -> Unit = { _, _ -> },
        onAttempt: (attempt: Int, result: T?, exception: Exception?) -> Unit = { _, _, _ -> },
        operation: () -> T,
    ): T {
        val config = RetryConfig.custom<T>()
            .maxAttempts(maxAttempts)
            .waitDuration(Duration.ofMillis(backoffMillis))
            .retryOnResult(Predicate(shouldRetryResult))
            .retryOnException { throwable ->
                throwable is Exception && shouldRetryException(throwable)
            }
            .build()
        val retry = Retry.of(name, config)
        retry.eventPublisher.onRetry { event ->
            val cause = event.lastThrowable?.message ?: "retryable result"
            onRetry(event.numberOfRetryAttempts + 1, cause)
        }
        var attempt = 0
        return retry.executeCallable(Callable {
            attempt++
            try {
                operation().also { onAttempt(attempt, it, null) }
            } catch (exception: Exception) {
                onAttempt(attempt, null, exception)
                throw exception
            }
        })
    }
}