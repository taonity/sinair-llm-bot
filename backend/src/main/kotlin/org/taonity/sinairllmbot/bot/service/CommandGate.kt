package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

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
