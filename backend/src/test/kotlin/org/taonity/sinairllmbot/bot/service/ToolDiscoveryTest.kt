package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.tools.*
import tools.jackson.module.kotlin.jacksonObjectMapper

class ToolDiscoveryTest {
    @Test
    fun `tools load lazily and unoffered capabilities cannot execute`() {
        var discoveries = 0
        val contributor = object : LlmToolContributor {
            override val capability = ToolCapability.REPOSITORY
            override fun definitions(context: ToolExecutionContext): List<Tool> {
                discoveries++
                return listOf(Tool.function("read_file", "Read", emptyMap()))
            }
            override fun supports(name: String) = name == "read_file"
            override fun execute(context: ToolExecutionContext, name: String, argumentsJson: String) = "evidence"
        }
        val session = LlmToolDispatcher(listOf(contributor), jacksonObjectMapper())
            .open(ToolExecutionContext("#room", "trigger", "bot"), setOf(ToolCapability.REPOSITORY))
        assertThat(session.definitions()).hasSize(1)
        assertThat(discoveries).isZero()
        assertThat(session.execute("read_file", "{}")).startsWith("ERROR:")
        assertThat(session.execute("discover_tools", """{"capability":"REPOSITORY_WRITE"}""")).startsWith("ERROR:")
        session.execute("discover_tools", """{"capability":"REPOSITORY"}""")
        assertThat(session.execute("read_file", "{}")).isEqualTo("evidence")
        assertThat(session.readOnlyTools).contains("read_file")
        assertThat(discoveries).isEqualTo(1)
    }
}