package org.taonity.sinairllmbot.observability.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.taonity.sinairllmbot.user.repository.UserRepository
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

@Component
class AppMetricsComponent(
    private val meterRegistry: MeterRegistry,
    private val userRepository: UserRepository
) {
    companion object {
        private const val APP_USERS_COUNT: String = "app.users.count"
    }

    @PostConstruct
    fun init() {
        meterRegistry.gauge(APP_USERS_COUNT, userRepository) { it.count().toDouble() }
    }
}
