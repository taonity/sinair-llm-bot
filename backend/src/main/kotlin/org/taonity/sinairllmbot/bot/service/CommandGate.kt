package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

/**
 * Zero-token pre-filter limited to explicit bot commands (mute / un-mute).
 *
 * Judging intent — whether the bot is addressed, whether a message is noise, whether it needs fresh
 * info — is left entirely to the cheap gate-tier LLM ([MessageTriageService]); we deliberately do
 * NOT use keyword/noise heuristics for that, since the gate model is cheap and far more reliable.
 *
 * - [CommandDecision.STOP_BOT] / [CommandDecision.START_BOT] — the message is a mute/un-mute command.
 * - [CommandDecision.NONE]     — not a command; hand off to the triage LLM.
 */
@Component
class CommandGate(
    private val settings: BotSettings,
) {
    private val botProperties get() = settings.bot()

    fun evaluate(message: ChatMessageEntity): CommandDecision {
        val text = message.messageText.trim()
        return when {
            text.equals(botProperties.persona.stopCommand, ignoreCase = true) -> CommandDecision.STOP_BOT
            text.equals(botProperties.persona.startCommand, ignoreCase = true) -> CommandDecision.START_BOT
            else -> CommandDecision.NONE
        }
    }
}

enum class CommandDecision {
    STOP_BOT,
    START_BOT,
    NONE,
}
