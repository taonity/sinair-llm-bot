package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Local/test WireMock stub for the OpenAI-compatible LLM endpoint, mirroring [GoogleWireMockConfig].
 *
 * Activated by the `stub-llm` profile. Mappings live in the `llm-stubs` module under
 * `wiremock/llm/mappings` and return deterministic responses for the three call kinds
 * (summary / classifier-JSON / reply). With this profile, point `app.llm.base-url` at the stub port
 * (`app.stub.llm.port`, see `application-stub-llm.yaml`).
 */
@Configuration
@Profile("stub-llm")
class LlmWireMockConfig {

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        @Volatile
        private var server: WireMockServer? = null

        private fun start(port: Int): WireMockServer =
            server ?: synchronized(this) {
                server ?: WireMockServer(
                    wireMockConfig()
                        .port(port)
                        .usingFilesUnderClasspath("wiremock/llm")
                        .globalTemplating(true)
                ).also {
                    it.start()
                    Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
                    LOGGER.info { "LLM WireMock stub started on port ${it.port()}" }
                    server = it
                }
            }

        @JvmStatic
        @Bean
        fun llmWireMockInitializer(
            @Value("\${app.stub.llm.port}") port: Int,
        ): BeanFactoryPostProcessor = BeanFactoryPostProcessor {
            start(port) // Force init before other beans
        }
    }

    @Bean(destroyMethod = "")
    fun llmWireMockServer(@Value("\${app.stub.llm.port}") port: Int): WireMockServer = start(port)
}
