package org.taonity.sinairllmbot.local

import com.github.tomakehurst.wiremock.WireMockServer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.DatabaseMetaData
import javax.sql.DataSource

class DemoPayloadInitializerTest {
    @ParameterizedTest
    @ValueSource(strings = ["jdbc:postgresql://localhost/production", "jdbc:h2:file:./h2_db", "jdbc:h2:mem:test"])
    fun `refuses to record demo payloads outside the isolated database`(url: String) {
        val metadata = mock(DatabaseMetaData::class.java)
        val connection = mock(Connection::class.java)
        val dataSource = mock(DataSource::class.java)
        val llmServer = mock(WireMockServer::class.java)
        val llmClient = mock(LlmClient::class.java)
        val tracker = mock(PipelineLlmUsageTracker::class.java)
        val settings = mock(BotSettings::class.java)
        `when`(metadata.url).thenReturn(url)
        `when`(connection.metaData).thenReturn(metadata)
        `when`(dataSource.connection).thenReturn(connection)

        assertThrows(IllegalArgumentException::class.java) {
            DemoPayloadInitializer(dataSource, llmServer, llmClient, tracker, settings, ObjectMapper())
                .run(DefaultApplicationArguments())
        }

        verifyNoInteractions(llmServer, llmClient, tracker, settings)
    }
}
