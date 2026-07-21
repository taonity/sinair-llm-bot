package org.taonity.sinairllmbot.health

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

@Component("google")
class GoogleHealthIndicator(
    @Value("\${spring.security.oauth2.client.provider.google.user-info-uri}") private val userInfoUri: String,
) : HealthIndicator {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val MAX_BODY_PREVIEW_CHARS = 160
        private val REST_CLIENT = RestClient.create()
    }

    override fun health(): Health {
        val start = Instant.now()
        return try {
            val responseEntity = REST_CLIENT.get()
                .uri(userInfoUri)
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, _ -> } // 4xx = reachable, suppress default error
                .toEntity(String::class.java)
            val elapsedMs = Duration.between(start, Instant.now()).toMillis()
            val statusCode = responseEntity.statusCode
            // 4xx (e.g. 401 Unauthorized without a token) still means Google is reachable
            val builder = if (!statusCode.is5xxServerError) Health.up() else Health.down()
            builder.withDetail("url", userInfoUri)
                .withDetail("statusCode", statusCode.value())
                .withDetail("responseTimeMs", elapsedMs)
            if (statusCode.is5xxServerError) {
                builder.withDetail("responsePreview", responseEntity.body?.take(MAX_BODY_PREVIEW_CHARS) ?: "")
            }
            builder.build()
        } catch (exception: Exception) {
            val elapsedMs = Duration.between(start, Instant.now()).toMillis()
            LOGGER.warn(exception) { "Google availability check failed for $userInfoUri" }
            Health.down()
                .withDetail("url", userInfoUri)
                .withDetail("responseTimeMs", elapsedMs)
                .withDetail("error", exception.message ?: exception::class.simpleName ?: "unknown")
                .build()
        }
    }
}
