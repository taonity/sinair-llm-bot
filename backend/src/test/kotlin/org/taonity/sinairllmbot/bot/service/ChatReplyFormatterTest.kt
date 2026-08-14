package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChatReplyFormatterTest {
    @Test
    fun `leaves a six line response unchanged`() {
        val response = "a".repeat(810)

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(response)
    }

    @Test
    fun `wraps a long line that would render beyond six lines`() {
        val response = "a".repeat(811)

        assertThat(ChatReplyFormatter.wrapLongReply(response))
            .isEqualTo("```\n$response\n```")
    }

    @Test
    fun `leaves an already wrapped long response unchanged`() {
        val response = "```\n${"a".repeat(811)}\n```"

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(response)
    }

    @Test
    fun `normalizes partial blocks into one block around the entire response`() {
        val response = "intro\n```\n${"a".repeat(811)}\n```\noutro"

        val formatted = ChatReplyFormatter.wrapLongReply(response)

        assertThat(formatted).startsWith("```\nintro\n")
        assertThat(formatted).endsWith("\noutro\n```")
        assertThat(formatted.windowed(3).count { it == "```" }).isEqualTo(2)
    }

    @Test
    fun `wraps seven short logical lines`() {
        val response = (1..7).joinToString("\n") { "line $it" }

        assertThat(ChatReplyFormatter.wrapLongReply(response))
            .isEqualTo("```\n$response\n```")
    }
}