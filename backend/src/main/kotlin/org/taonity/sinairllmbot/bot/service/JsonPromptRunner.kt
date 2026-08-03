package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.client.LlmResult
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailure
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailureTracker
import org.taonity.sinairllmbot.config.BotSettings

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
