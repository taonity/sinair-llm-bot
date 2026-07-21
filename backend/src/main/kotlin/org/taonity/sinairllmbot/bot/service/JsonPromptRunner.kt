package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.client.LlmResult
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailure
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailureTracker
import org.taonity.sinairllmbot.config.BotSettings

/**
 * Adds resilience to the prompts that expect a JSON reply (triage, critic): when the model's output
 * can't be deserialized, the same prompt is re-issued up to `app.llm.json-retry-attempts` times
 * before giving up. Every failed attempt is logged and recorded on the current pipeline run (via
 * [JsonParseFailureTracker]) so the console can show how often the model returned malformed JSON and
 * the exact payload that failed.
 *
 * Fail-open by contract: the caller decides how to degrade when all attempts are exhausted (the
 * runner simply returns null), so a flaky model can never break the reply pipeline.
 */
@Component
class JsonPromptRunner(
    private val settings: BotSettings,
    private val failureTracker: JsonParseFailureTracker,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        // Bound the stored/logged payload: malformed JSON is usually short, but a runaway response
        // must not bloat the pipeline_run row or the logs.
        private const val MAX_PAYLOAD_CHARS = 4000
    }

    /**
     * Runs [call] and parses its content with [parse], retrying on any parse failure.
     *
     * @param label identifies the prompt in logs and the trace (e.g. "triage", "critic").
     * @param call issues the LLM request; may return null when the call itself fails/empties.
     * @param parse deserializes the raw content, returning null on failure (its own recovery, such
     *   as the triage salvage path, counts as success and stops the retries).
     * @return the first successfully parsed value, or null when every attempt failed.
     */
    fun <T : Any> run(
        label: String,
        call: () -> LlmResult?,
        parse: (String) -> T?,
    ): T? {
        val attempts = settings.llm().jsonRetryAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            val content = call()?.content
            if (content == null) {
                recordFailure(label, attempt, attempts, "<no content>", "empty LLM response")
                continue
            }
            val parsed = parse(content)
            if (parsed != null) {
                if (attempt > 1) LOGGER.info { "JSON prompt '$label' recovered on attempt $attempt/$attempts" }
                return parsed
            }
            recordFailure(label, attempt, attempts, content, "unparseable JSON (len=${content.length})")
        }
        return null
    }

    private fun recordFailure(label: String, attempt: Int, attempts: Int, payload: String, reason: String) {
        LOGGER.warn { "JSON prompt '$label' attempt $attempt/$attempts failed: $reason" }
        failureTracker.record(JsonParseFailure(label, attempt, payload.take(MAX_PAYLOAD_CHARS)))
    }
}
