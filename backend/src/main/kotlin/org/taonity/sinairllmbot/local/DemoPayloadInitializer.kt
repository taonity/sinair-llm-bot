package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper
import javax.sql.DataSource

@Component
@Profile("demo")
@Order(1)
class DemoPayloadInitializer(
    private val dataSource: DataSource,
    @Qualifier("llmWireMockServer") private val llmServer: WireMockServer,
    private val llmClient: LlmClient,
    private val usageTracker: PipelineLlmUsageTracker,
    private val settings: BotSettings,
    private val objectMapper: ObjectMapper,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        dataSource.connection.use { connection ->
            require(connection.metaData.url == "jdbc:h2:mem:sinair-demo") {
                "Demo payloads require the isolated in-memory demo database"
            }
        }
        val fixture = ClassPathResource("demo/llm-exchange.json").inputStream.use(objectMapper::readTree)
        llmServer.stubFor(
            post(urlEqualTo("/v1/chat/completions")).atPriority(1).willReturn(
                aResponse().withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(fixture.path("response")))
            )
        )
        val messages = listOf(ChatMessage.system(settings.bot().persona.prompt.text)) +
            fixture.required("messages").values().map {
                ChatMessage(it.required("role").asString(), it.required("content").asString())
            }
        usageTracker.begin()
        try {
            val result = requireNotNull(llmClient.complete("smart-glm", messages))
            val calls = usageTracker.drain()
            require(calls.size == 1 && calls.single().requestPayload.isNotBlank() && calls.single().responsePayload.isNotBlank())
            dataSource.connection.use { connection ->
                connection.prepareStatement("UPDATE pipeline_run SET llm_usage_json = ?, total_tokens = ? WHERE id = ?").use { statement ->
                    statement.setString(1, objectMapper.writeValueAsString(calls))
                    statement.setInt(2, calls.sumOf { it.tokens })
                    statement.setString(3, "demo-pipeline-01")
                    check(statement.executeUpdate() == 1)
                }
                connection.prepareStatement("UPDATE outbound_message SET message_text = ? WHERE id = ?").use { statement ->
                    statement.setString(1, result.content)
                    statement.setString(2, "demo-outbound-01")
                    check(statement.executeUpdate() == 1)
                }
            }
        } finally {
            usageTracker.drain()
        }
    }
}
