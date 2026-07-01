package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

/**
 * Zero-token pre-filter that decides whether a message is worth spending any LLM tokens on.
 *
 * - [GateDecision.REPLY_NOW]  — the bot is directly addressed; skip the classifier, go to reply.
 * - [GateDecision.IGNORE]     — obvious noise (empty, one-word acks, the bot's own message).
 * - [GateDecision.MAYBE]      — ambiguous; let the cheap interest classifier decide.
 */
@Component
class HeuristicGate(
    private val botProperties: BotProperties,
) {
    private companion object {
        private val NOISE_TOKENS = setOf(
            "pass", "test", "ok", "ок", "норм", "+", "-", "ага", "угу", "да", "нет", "lol", "хм",
        )
        private val URL_ONLY = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    }

    fun evaluate(message: ChatMessageEntity): GateDecision {
        val botName = botProperties.persona.name
        val text = message.messageText.trim()

        if (message.senderLogin.equals(botName, ignoreCase = true)) {
            return GateDecision.IGNORE
        }

        if (text.equals(botProperties.persona.stopCommand, ignoreCase = true)) {
            return GateDecision.STOP_BOT
        }
        if (text.equals(botProperties.persona.startCommand, ignoreCase = true)) {
            return GateDecision.START_BOT
        }

        if (isDirectlyAddressed(text)) {
            return GateDecision.REPLY_NOW
        }

        if (isNoise(text)) {
            return GateDecision.IGNORE
        }

        return GateDecision.MAYBE
    }

    /**
     * True when the message addresses the bot by its name or one of its [BotProperties.Persona.aliases].
     *
     * Matching is whole-word only: a trigger must be delimited by start/end of text, whitespace, an
     * `@`/`>` opener or trailing punctuation. This prevents a trigger that merely appears inside an
     * unrelated word (e.g. "сега" inside "сегатроника") from firing.
     */
    private fun isDirectlyAddressed(text: String): Boolean {
        val lower = text.lowercase()
        return triggerNames().any { trigger ->
            lower.contains("@$trigger") || wordBoundaryRegex(trigger).containsMatchIn(lower)
        }
    }

    private fun triggerNames(): List<String> =
        (listOf(botProperties.persona.name) + botProperties.persona.aliases)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

    private fun wordBoundaryRegex(trigger: String): Regex =
        Regex("(^|[\\s>@(\"'«])@?${Regex.escape(trigger)}([\\s,.:;!?)\"'»]|$)")

    private fun isNoise(text: String): Boolean {
        if (text.length < 2) return true
        if (URL_ONLY.matches(text)) return true
        val normalized = text.lowercase().trim('.', '!', '?', ')', '(', ' ')
        if (normalized in NOISE_TOKENS) return true
        // Emoji-only / punctuation-only short messages.
        val hasLetters = text.any { it.isLetter() }
        return !hasLetters && text.length < 8
    }
}

enum class GateDecision {
    REPLY_NOW,
    MAYBE,
    IGNORE,
    STOP_BOT,
    START_BOT,
}
