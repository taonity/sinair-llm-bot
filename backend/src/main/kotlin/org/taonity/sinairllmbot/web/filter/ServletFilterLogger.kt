package org.taonity.sinairllmbot.web.filter

import jakarta.servlet.ServletContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class ServletFilterLogger(private val servletContext: ServletContext) : CommandLineRunner {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun run(vararg args: String) {
        servletContext.filterRegistrations.forEach { (name, registration) ->
            LOGGER.debug {
                "Filter registered: $name, class: ${registration.className}, " +
                        "mapping: ${registration.urlPatternMappings}"
            }
        }
    }
}
