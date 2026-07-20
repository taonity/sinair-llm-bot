package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("stub-google")
class GoogleWireMockConfig {

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        @Volatile
        private var server: WireMockServer? = null

        private fun start(port: Int): WireMockServer =
            server ?: synchronized(this) {
                server ?: WireMockServer(
                    wireMockConfig()
                        .port(port)
                        .usingFilesUnderClasspath("wiremock/google")
                        .globalTemplating(true)
                ).also {
                    it.start()
                    Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
                    LOGGER.info { "Google WireMock stub started on port ${it.port()}" }
                    server = it
                }
            }

        @JvmStatic
        @Bean
        fun googleWireMockInitializer(
            @Value("\${app.stub.google.port}") port: Int,
        ): BeanFactoryPostProcessor = BeanFactoryPostProcessor {
            start(port) // Force init before other beans
        }
    }

    @Bean(destroyMethod = "")
    fun googleWireMockServer(@Value("\${app.stub.google.port}") port: Int): WireMockServer = start(port)
}
