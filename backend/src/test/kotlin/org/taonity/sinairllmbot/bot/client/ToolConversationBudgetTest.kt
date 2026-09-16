package org.taonity.sinairllmbot.bot.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolConversationBudgetTest {
    @Test
    fun `large retained reasoning becomes bounded evidence for a fresh final turn`() {
        val conversation = mutableListOf(
            ChatMessage.system("Rules"), ChatMessage.user("Original task"),
            ChatMessage("assistant", toolCalls = listOf(ToolCall(id = "call")), reasoningDetails = "opaque".repeat(1000)),
            ChatMessage.tool("call", "Useful finding"),
        )
        assertThat(ToolConversationBudget.compact(conversation, 1000)).isTrue()
        assertThat(ToolConversationBudget.size(conversation)).isLessThanOrEqualTo(1000)
        assertThat(conversation[1].content).isEqualTo("Original task")
        assertThat(conversation.last().content.toString()).contains("Useful finding", "omitted")
        assertThat(conversation.any { it.role == "tool" }).isFalse()
    }

    @Test
    fun `compaction keeps tool pairs and the original request`() {
        val reasoning = listOf(mapOf("type" to "reasoning.encrypted", "data" to "opaque"))
        val conversation = mutableListOf(
            ChatMessage.user("Original request"),
            ChatMessage("assistant", toolCalls = listOf(ToolCall(id = "call")), reasoningDetails = reasoning),
            ChatMessage.tool("call", "evidence".repeat(1000)),
        )

        assertThat(ToolConversationBudget.compact(conversation, 1000)).isTrue()
        assertThat(conversation.first().content).isEqualTo("Original request")
        assertThat(conversation[1].reasoningDetails).isEqualTo(reasoning)
        assertThat(conversation.last().toolCallId).isEqualTo("call")
        assertThat(conversation.last().content.toString()).contains("omitted").hasSizeLessThan(1000)
    }
}