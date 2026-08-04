package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component
import java.util.Collections

data class LlmCallUsage(
    val tier: String,
    val model: String,
    val tokens: Int,
    val tools: List<String> = emptyList(),
    val requestPayload: String = "",
    val responsePayload: String = "",
    val toolCalls: List<ToolCallEntry> = emptyList(),
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val attempt: Int = 1,
    val maxAttempts: Int = 1,
    val status: LlmCallStatus = LlmCallStatus.SUCCEEDED,
    val error: String? = null,
    val iteration: Int? = null,
    val totalIterations: Int? = null,
)

enum class LlmCallStatus {
    SUCCEEDED,
    FAILED,
}

data class ToolCallEntry(
    val name: String,
    val arguments: String,
    val result: String,
    val error: Boolean = false,
    val attempts: List<ToolCallAttempt> = emptyList(),
    val maxAttempts: Int = 1,
)

data class ToolCallAttempt(
    val attempt: Int,
    val result: String,
    val error: Boolean,
)

@Component
class PipelineLlmUsageTracker {
    private val calls = ThreadLocal<MutableList<LlmCallUsage>?>()

    fun begin() {
        calls.set(Collections.synchronizedList(mutableListOf()))
    }

    fun record(usage: LlmCallUsage) {
        calls.get()?.add(usage)
    }

    fun currentSink(): MutableList<LlmCallUsage>? = calls.get()

    fun <T> withSink(sink: MutableList<LlmCallUsage>?, block: () -> T): T {
        val previous = calls.get()
        calls.set(sink)
        return try {
            block()
        } finally {
            if (previous != null) calls.set(previous) else calls.remove()
        }
    }

    fun drain(): List<LlmCallUsage> {
        val recorded = calls.get().orEmpty().toList()
        calls.remove()
        return recorded
    }
}
