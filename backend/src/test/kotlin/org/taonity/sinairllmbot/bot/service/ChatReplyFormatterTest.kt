package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChatReplyFormatterTest {
    @Test
    fun `removes unsupported bold markers`() {
        assertThat(ChatReplyFormatter.normalize("this is **important**"))
            .isEqualTo("this is important")
    }

    @Test
    fun `preserves double asterisks inside fenced code`() {
        val response = "outside **bold**\n```\nvalue = 2 ** 3\n```"

        assertThat(ChatReplyFormatter.normalize(response))
            .isEqualTo("outside bold\n```\nvalue = 2 ** 3\n```")
    }

    @Test
    fun `collapses blank lines without joining quote and reply lines`() {
        val response = "> first quote\r\n\r\nreply\r\n> second quote\r\nnext reply"

        assertThat(ChatReplyFormatter.normalize(response))
            .isEqualTo("> first quote\nreply\n> second quote\nnext reply")
    }

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
    fun `leaves a single-line wrapped response unchanged when no description can be separated`() {
        val response = "```\n${"a".repeat(811)}\n```"

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(response)
    }

    @Test
    fun `moves a description out of a whole-message fenced block`() {
        val details = (1..7).joinToString("\n") { "- detail $it" }
        val response = "```\nBrief description of the structured details.\n$details\n```"

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(
            "Brief description of the structured details.\n```\n$details\n```",
        )
    }

    @Test
    fun `keeps a brief description outside its long response block`() {
        val response = "Brief description of the structured details below.\n```\n${"a".repeat(811)}\n```"

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(response)
    }

    @Test
    fun `preserves a fenced block with surrounding prose`() {
        val response = "Description of the details.\n```\n${"a".repeat(811)}\n```\nShort conclusion."

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(response)
    }

    @Test
    fun `removes blank lines around a fenced block and conclusion`() {
        val response = "Description.\n\n```\n${"a".repeat(811)}\n```\n\nConclusion."

        assertThat(ChatReplyFormatter.wrapLongReply(ChatReplyFormatter.normalize(response))).isEqualTo(
            "Description.\n```\n${"a".repeat(811)}\n```\nConclusion.",
        )
    }

    @Test
    fun `normalizes an unclosed block into one block around the entire response`() {
        val response = "intro\n```\n${"a".repeat(811)}\noutro"

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

    @Test
    fun `keeps multiple quotes functional in a long response`() {
        val response = "> first quote\nreply one\nreply two\n> second quote\nreply three\nreply four\nreply five"

        assertThat(ChatReplyFormatter.wrapLongReply(response)).isEqualTo(
            "> first quote\n```\nreply one\nreply two\n```\n" +
                "> second quote\n```\nreply three\nreply four\nreply five\n```",
        )
    }
}