package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("stub-google")
class GoogleWireMockConfig {

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        private val server: WireMockServer by lazy {
            WireMockServer(
                wireMockConfig()
                    .port(9561)
                    .usingFilesUnderClasspath("wiremock/google")
                    .globalTemplating(true)
            ).also {
                it.start()
                Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
                LOGGER.info { "Google WireMock stub started on port ${it.port()}" }
            }
        }

        @JvmStatic
        @Bean
        fun googleWireMockInitializer(): BeanFactoryPostProcessor = BeanFactoryPostProcessor {
            server // Force lazy init before other beans
        }
    }

    @Bean(destroyMethod = "")
    fun googleWireMockServer(): WireMockServer = server
}
