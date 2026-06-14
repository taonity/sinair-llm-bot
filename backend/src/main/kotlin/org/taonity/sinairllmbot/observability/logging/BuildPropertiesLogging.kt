package org.taonity.sinairllmbot.observability.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

@Component
@ConditionalOnBean(BuildProperties::class)
class BuildPropertiesLogging(
    private val buildProperties: BuildProperties

) : CommandLineRunner  {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun run(vararg args: String) {
        LOGGER.info { "BuildProperties - time: ${buildProperties.time}, version: ${buildProperties.version}" }
    }
}
