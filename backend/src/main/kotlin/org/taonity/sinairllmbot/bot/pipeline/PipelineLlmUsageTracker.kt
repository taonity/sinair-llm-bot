package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component

/**
 * One LLM call made during a pipeline run: which tier/model answered, its token cost, and the
 * server-tool set it was offered (e.g. web_search).
 */
data class LlmCallUsage(
    val tier: String,
    val model: String,
    val tokens: Int,
    val tools: List<String> = emptyList(),
)

/**
 * Thread-local accumulator of the LLM calls made while evaluating one pipeline run. The pipeline
 * runs entirely on a single (bot-debounce) thread, so a thread-local cleanly gathers every
 * [org.taonity.sinairllmbot.bot.client.LlmClient] call between [begin] and [drain] without threading
 * usage through every service. Purely observational: it must never affect the reply pipeline.
 */
@Component
class PipelineLlmUsageTracker {
    private val calls = ThreadLocal<MutableList<LlmCallUsage>?>()

    /** Starts (or resets) collection for the current thread's pipeline run. */
    fun begin() {
        calls.set(mutableListOf())
    }

    /** Records one completed LLM call; a no-op when no run is active on this thread. */
    fun record(usage: LlmCallUsage) {
        calls.get()?.add(usage)
    }

    /** Returns the calls recorded since [begin] and clears the thread-local. */
    fun drain(): List<LlmCallUsage> {
        val recorded = calls.get().orEmpty().toList()
        calls.remove()
        return recorded
    }
}
