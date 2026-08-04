package org.taonity.sinairllmbot.bot.grafana

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("bottest")
class GrafanaMcpConfigurationTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var properties: GrafanaMcpProperties

    @Test
    fun `Grafana MCP integration is disabled unless explicitly enabled`() {
        assertThat(properties.enabled).isFalse()
        assertThat(context.getBeansOfType(GrafanaMcpClient::class.java)).isEmpty()
        assertThat(context.getBeansOfType(GrafanaMcpToolContributor::class.java)).isEmpty()
    }
}