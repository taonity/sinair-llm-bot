package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component

/**
 * One failed attempt to deserialize a JSON-only prompt's response during a pipeline run: which
 * prompt produced it ([label], e.g. "triage"/"critic"), the 1-based [attempt] number, and the raw
 * [payload] the model returned that could not be parsed. Surfaced in the console "Pipelines" tab so
 * an operator can see how often the model returned malformed JSON and inspect the exact output.
 */
data class JsonParseFailure(
    val label: String,
    val attempt: Int,
    val payload: String,
)

/**
 * Thread-local accumulator of JSON-deserialization failures collected while evaluating one pipeline
 * run, mirroring [PipelineLlmUsageTracker]. The JSON-only prompts (triage, critic) run on the
 * single bot-debounce pipeline thread, so a thread-local gathers their retries between [begin] and
 * [drain] without threading state through every service. Purely observational: it must never affect
 * the reply pipeline.
 */
@Component
class JsonParseFailureTracker {
    private val failures = ThreadLocal<MutableList<JsonParseFailure>?>()

    /** Starts (or resets) collection for the current thread's pipeline run. */
    fun begin() {
        failures.set(mutableListOf())
    }

    /** Records one parse failure; a no-op when no run is active on this thread. */
    fun record(failure: JsonParseFailure) {
        failures.get()?.add(failure)
    }

    /** Returns the failures recorded since [begin] and clears the thread-local. */
    fun drain(): List<JsonParseFailure> {
        val recorded = failures.get().orEmpty().toList()
        failures.remove()
        return recorded
    }
}
