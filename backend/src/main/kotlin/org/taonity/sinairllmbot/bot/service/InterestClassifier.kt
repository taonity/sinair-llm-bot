package org.taonity.sinairllmbot.bot.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import tools.jackson.databind.ObjectMapper

/**
 * Cheap second stage: decides whether an ambiguous burst of conversation is worth a reply.
 * Uses the small "gate" tier with a tiny prompt and strict JSON output to keep tokens minimal.
 */
@Service
class InterestClassifier(
    private val llmClient: LlmClient,
    private val contextBuilder: ConversationContextBuilder,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val JSON_FENCE = Regex("^```(?:json)?|```$", RegexOption.IGNORE_CASE)
    }

    fun shouldRespond(roomTarget: String): InterestVerdict {
        val transcript = contextBuilder.recentTranscript(roomTarget, limit = 12)
        if (transcript.isBlank()) return InterestVerdict(false, "empty")

        val system = buildString {
            append("You decide whether a chat bot named '").append(botProperties.persona.name)
            append("' should jump into a ").append(botProperties.persona.language)
            append(" tech group chat right now. Reply only when the bot can add something valuable, ")
            append("funny, or a strong opinion — not for small talk or to state the obvious. ")
            append("Respond with a JSON object: {\"respond\": boolean, \"reason\": string}. ")
            append("Default to respond=false.")
        }
        val raw = llmClient.complete(
            tierName = llmProperties.gateTier,
            messages = listOf(ChatMessage.system(system), ChatMessage.user("RECENT CHAT:\n$transcript")),
            forceJson = true,
        ) ?: return InterestVerdict(false, "llm-unavailable")

        return parse(raw.content)
    }

    private fun parse(content: String): InterestVerdict {
        val cleaned = content.trim().lines()
            .filterNot { JSON_FENCE.containsMatchIn(it.trim()) && it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        return try {
            objectMapper.readValue(cleaned, InterestVerdict::class.java)
        } catch (exception: Exception) {
            LOGGER.warn { "Failed to parse interest verdict: '$content' (${exception.message})" }
            InterestVerdict(false, "parse-error")
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InterestVerdict(
    val respond: Boolean = false,
    val reason: String = "",
)
