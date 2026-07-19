package org.taonity.sinairllmbot.bot.service

import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity

/**
 * What caused a rolling-summary refresh, so the persisted "summary" pipeline run can attribute it
 * (the console shows which message, or which job, triggered the refresh). New sources just add a
 * variant here.
 */
sealed interface SummaryRefreshTrigger {
    /** A short human label for the source, shown as a stage field on the summary run. */
    val label: String

    /** A newly ingested message drove the bot evaluation that refreshed the summary. */
    data class Message(val message: ChatMessageEntity) : SummaryRefreshTrigger {
        override val label: String = "message @${message.senderLogin}"
    }

    /** A scheduled/background job refreshed the summary (e.g. retention compaction). */
    data class Job(val name: String) : SummaryRefreshTrigger {
        override val label: String = "job: $name"
    }
}
