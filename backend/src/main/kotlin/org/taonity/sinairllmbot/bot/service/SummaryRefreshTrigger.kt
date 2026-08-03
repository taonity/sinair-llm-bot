package org.taonity.sinairllmbot.bot.service

import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

sealed interface SummaryRefreshTrigger {
    val label: String

    data class Message(val message: ChatMessageEntity) : SummaryRefreshTrigger {
        override val label: String = "message @${message.senderLogin}"
    }

    data class Job(val name: String) : SummaryRefreshTrigger {
        override val label: String = "job: $name"
    }
}
