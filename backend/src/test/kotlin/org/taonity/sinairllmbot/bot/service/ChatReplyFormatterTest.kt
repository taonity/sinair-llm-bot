package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class ChatReplyFormatterTest {
    private val mapper = jacksonObjectMapper()
    private val renderer = ReplyDocumentRenderer(mapper)

    @Test
    fun `strips unsupported bold outside code only`() {
        assertThat(ChatReplyFormatter.normalize("**Important**\n```\nvalue = 2 ** 3\n```"))
            .isEqualTo("Important```value = 2 ** 3```")
    }

    @Test
    fun `preserves blank lines and first line inside code`() {
        val code = "fun example() {\n\n    println(1)\n}"
        assertThat(renderer.render("```\n$code\n```" )).isEqualTo("Подробности:```$code```")
    }

    @Test
    fun `both long prose and short existing blocks have an outside lead`() {
        assertThat(renderer.render("a".repeat(811))).startsWith("Подробности:```")
        assertThat(renderer.render("```short details```" )).startsWith("Подробности:```")
    }

    @Test
    fun `extracts a prose lead without moving a code line`() {
        val details = (1..7).joinToString("\n") { "- detail $it" }
        assertThat(renderer.render("My conclusion.\n$details")).isEqualTo("My conclusion.```$details```")
    }

    @Test
    fun `renders typed blocks without guessing where the header ends`() {
        val code = "fun example() {\n\n    println(1)\n}"
        val response = mapper.writeValueAsString(mapOf(
            "lead" to "Working example.",
            "blocks" to listOf(mapOf("kind" to "code", "text" to code)),
        ))
        assertThat(renderer.render(response)).isEqualTo("Working example.```$code```")
    }

    @Test
    fun `quotes remain outside detail blocks`() {
        val response = mapper.writeValueAsString(mapOf(
            "lead" to "The distinction matters.",
            "blocks" to listOf(mapOf("kind" to "quote", "text" to "original"), mapOf("kind" to "prose", "text" to "explanation")),
        ))
        assertThat(renderer.render(response)).contains("\n> original\n").contains("Подробности:```explanation```")
    }

    @Test
    fun `short replies stay short and malformed JSON is not leaked`() {
        assertThat(renderer.render("short answer")).isEqualTo("short answer")
        assertThat(renderer.render("{\"lead\":\"unfinished")).doesNotContain("{", "lead")
    }

    @Test
    fun `closes an unfinished legacy block`() {
        assertThat(renderer.render("Description.```details")).isEqualTo("Description.```details```")
    }

    @Test
    fun `chat size limit never leaves partial executable code`() {
        val code = "println(1)\n".repeat(100)
        val rendered = ChatReplyFormatter.limit("Example.```$code```", 100)
        assertThat(rendered).hasSizeLessThanOrEqualTo(100).contains("не поместилась").doesNotContain("println", "```")
        assertThat(ChatReplyFormatter.limit("Example.```println(1)```", 100)).isEqualTo("Example.```println(1)```")
    }
}