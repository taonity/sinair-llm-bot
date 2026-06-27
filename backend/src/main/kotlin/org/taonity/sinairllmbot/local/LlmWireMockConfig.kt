package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Local/test WireMock stub for the OpenAI-compatible LLM endpoint, mirroring [GoogleWireMockConfig].
 *
 * Activated by the `stub-llm` profile. Mappings live in the `llm-stubs` module under
 * `wiremock/llm/mappings` and return deterministic responses for the three call kinds
 * (summary / classifier-JSON / reply). With this profile, point `app.llm.base-url` at port 9562
 * (see `application-stub-llm.yaml`).
 */
@Configuration
@Profile("stub-llm")
class LlmWireMockConfig {

    companion object {
        private val LOGGER = KotlinLogging.logger {}

        private val server: WireMockServer by lazy {
            WireMockServer(
                wireMockConfig()
                    .port(9562)
                    .usingFilesUnderClasspath("wiremock/llm")
                    .globalTemplating(true)
            ).also {
                it.start()
                Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
                LOGGER.info { "LLM WireMock stub started on port ${it.port()}" }
            }
        }

        @JvmStatic
        @Bean
        fun llmWireMockInitializer(): BeanFactoryPostProcessor = BeanFactoryPostProcessor {
            server // Force lazy init before other beans
        }
    }

    @Bean(destroyMethod = "")
    fun llmWireMockServer(): WireMockServer = server
}
