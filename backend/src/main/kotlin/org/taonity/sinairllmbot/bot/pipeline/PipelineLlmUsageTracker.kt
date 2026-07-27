package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component
import java.util.Collections

/**
 * One LLM call made during a pipeline run: which tier/model answered, its token cost, the
 * server-tool set it was offered (e.g. web_search), and the raw provider request/response payloads
 * (stored for later inspection in the console).
 *
 * [toolCalls] holds the structured client-tool invocations executed during an agentic
 * ([org.taonity.sinairllmbot.bot.client.LlmClient.completeWithTools]) round: the tool name, the
 * model's arguments JSON, and the result string we fed back. Empty for non-tool calls and for
 * server-side tools (e.g. web_search) whose execution is opaque to us.
 */
data class LlmCallUsage(
    val tier: String,
    val model: String,
    val tokens: Int,
    val tools: List<String> = emptyList(),
    val requestPayload: String = "",
    val responsePayload: String = "",
    val toolCalls: List<ToolCallEntry> = emptyList(),
    /** Prompt (input) tokens billed for this call, from the provider's usage. */
    val promptTokens: Int = 0,
    /** Completion (output) tokens billed for this call, from the provider's usage. */
    val completionTokens: Int = 0,
)

/**
 * One client-tool call executed during an agentic LLM round: the tool [name] the model asked for,
 * its [arguments] JSON string, and the [result] string we returned to the model. [error] is set when
 * the tool threw (the result then carries the `ERROR: ...` message). Compact and self-contained so
 * the console can render the full tool-call exchange without opening the raw payloads.
 */
data class ToolCallEntry(
    val name: String,
    val arguments: String,
    val result: String,
    val error: Boolean = false,
)

/**
 * Thread-local accumulator of the LLM calls made while evaluating one pipeline run. The pipeline
 * runs mostly on a single (bot-debounce) thread, so a thread-local cleanly gathers every
 * [org.taonity.sinairllmbot.bot.client.LlmClient] call between [begin] and [drain] without threading
 * usage through every service. Reply candidates are generated on worker threads, so the sink is a
 * synchronized list that those threads bind to via [withSink]. Purely observational: it must never
 * affect the reply pipeline.
 */
@Component
class PipelineLlmUsageTracker {
    private val calls = ThreadLocal<MutableList<LlmCallUsage>?>()

    /** Starts (or resets) collection for the current thread's pipeline run. */
    fun begin() {
        calls.set(Collections.synchronizedList(mutableListOf()))
    }

    /** Records one completed LLM call; a no-op when no run is active on this thread. */
    fun record(usage: LlmCallUsage) {
        calls.get()?.add(usage)
    }

    /** The sink of the run active on this thread, so a worker thread can re-bind it via [withSink]. */
    fun currentSink(): MutableList<LlmCallUsage>? = calls.get()

    /**
     * Binds [sink] as the active collection for the duration of [block] on the current thread, then
     * restores the previous binding. Used to carry a run's sink into async candidate-generation
     * worker threads so their LLM calls are still recorded against the run.
     */
    fun <T> withSink(sink: MutableList<LlmCallUsage>?, block: () -> T): T {
        val previous = calls.get()
        calls.set(sink)
        return try {
            block()
        } finally {
            if (previous != null) calls.set(previous) else calls.remove()
        }
    }

    /** Returns the calls recorded since [begin] and clears the thread-local. */
    fun drain(): List<LlmCallUsage> {
        val recorded = calls.get().orEmpty().toList()
        calls.remove()
        return recorded
    }
}
