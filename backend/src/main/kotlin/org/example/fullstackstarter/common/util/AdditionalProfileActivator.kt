package org.example.fullstackstarter.common.util

import org.springframework.boot.SpringApplication
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment


class AdditionalProfileActivator : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication
    ) {
        val additionalProfiles = environment.getProperty("app.additional-profiles")

        additionalProfiles?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { environment.addActiveProfile(it) }
    }
}
