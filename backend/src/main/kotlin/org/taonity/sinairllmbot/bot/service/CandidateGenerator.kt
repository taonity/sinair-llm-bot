package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.LlmProperties
import java.util.concurrent.CompletableFuture

/**
 * Draws several independent reply candidates for one message so the critic has diverse drafts to
 * choose between. Candidates are generated in parallel (they are independent calls), so the added
 * latency stays close to a single call, and at a raised temperature to keep them varied.
 */
@Service
class CandidateGenerator(
    private val llmClient: LlmClient,
    private val llmProperties: LlmProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun generate(prompt: ReplyPrompt): List<String> {
        val count = llmProperties.critic.candidateCount.coerceAtLeast(1)
        val messages = listOf(ChatMessage.system(prompt.system), prompt.userMessage)

        val futures = (1..count).map {
            CompletableFuture.supplyAsync {
                llmClient.complete(
                    tierName = prompt.tierName,
                    messages = messages,
                    webSearch = prompt.webSearch,
                    temperatureOverride = llmProperties.critic.candidateTemperature,
                )
            }
        }

        val candidates = futures
            .mapNotNull { runCatching { it.join() }.getOrNull() }
            .map { it.content.trim() }
            .filter { it.isNotBlank() }

        LOGGER.info { "Generated ${candidates.size}/$count reply candidate(s)" }
        return candidates
    }
}
