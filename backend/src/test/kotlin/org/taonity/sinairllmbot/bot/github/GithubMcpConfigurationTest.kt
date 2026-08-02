package org.taonity.sinairllmbot.bot.github

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.taonity.sinairllmbot.bot.config.GithubSettings
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@ActiveProfiles("bottest")
class GithubMcpConfigurationTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var settings: GithubSettings

    @Test
    fun `MCP and write contributors are disabled in test configuration`() {
        assertThat(settings.github().mcp.enabled).isFalse()
        assertThat(settings.github().mcp.writeEnabled).isFalse()
        assertThat(context.getBeansOfType(GithubMcpClient::class.java)).isEmpty()
        assertThat(context.getBeansOfType(GithubMcpReadToolContributor::class.java)).isEmpty()
        assertThat(context.getBeansOfType(GithubMcpWriteToolContributor::class.java)).isEmpty()
        assertThat(context.getBeansOfType(GithubToolService::class.java)).hasSize(1)
    }
}

class GithubMcpClientTest {
    @Test
    fun `tool outside the allowlist is rejected before connecting`() {
        val client = GithubMcpClient(
            settings = mock(GithubSettings::class.java),
            objectMapper = mock(ObjectMapper::class.java),
        )

        val result = client.execute("issue_write", "{}", setOf("search_code"))

        assertThat(result).isEqualTo("ERROR: GitHub MCP tool 'issue_write' is not allowed.")
    }
}