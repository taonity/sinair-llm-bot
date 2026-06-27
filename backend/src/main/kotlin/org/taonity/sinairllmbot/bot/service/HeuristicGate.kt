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
        // Short, content-free messages that never deserve a reply on their own.
        private val NOISE_TOKENS = setOf(
            "pass", "test", "ok", "ок", "норм", "+", "-", "ага", "угу", "да", "нет", "lol", "хм",
        )
        private val URL_ONLY = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    }

    fun evaluate(message: ChatMessageEntity): GateDecision {
        val botName = botProperties.persona.name
        val text = message.messageText.trim()

        // Never react to our own messages.
        if (message.senderLogin.equals(botName, ignoreCase = true)) {
            return GateDecision.IGNORE
        }

        if (isDirectlyAddressed(text, botName)) {
            return GateDecision.REPLY_NOW
        }

        if (isNoise(text)) {
            return GateDecision.IGNORE
        }

        return GateDecision.MAYBE
    }

    private fun isDirectlyAddressed(text: String, botName: String): Boolean {
        val lower = text.lowercase()
        val name = botName.lowercase()
        // Mention (@name), or the message opens by naming the bot.
        return lower.contains("@$name") ||
            lower.startsWith(name) ||
            Regex("(^|\\s|>)@?${Regex.escape(name)}([\\s,:!?]|$)").containsMatchIn(lower)
    }

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
}
