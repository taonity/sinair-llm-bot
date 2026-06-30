package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.bot.repository.RoomSummaryRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant

/**
 * Maintains a rolling, compressed summary per room so the bot has long-term context without
 * paying to resend full history on every reply. Refreshed with the cheap "gate" tier, and only
 * once enough new messages have accumulated.
 */
@Service
class RoomSummaryService(
    private val roomSummaryRepository: RoomSummaryRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val contextBuilder: ConversationContextBuilder,
    private val llmClient: LlmClient,
    private val botProperties: BotProperties,
    private val llmProperties: LlmProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional(readOnly = true)
    fun currentSummary(roomTarget: String): String =
        roomSummaryRepository.findByRoomTarget(roomTarget)?.summary.orEmpty()

    @Transactional
    fun refreshIfStale(roomTarget: String) {
        refreshInternal(roomTarget, force = false)
    }

    @Transactional
    fun forceRefresh(roomTarget: String) {
        refreshInternal(roomTarget, force = true)
    }

    private fun refreshInternal(roomTarget: String, force: Boolean) {
        val existing = roomSummaryRepository.findByRoomTarget(roomTarget)
        val totalMessages = chatMessageRepository.countByRoomTarget(roomTarget).toInt()
        val sinceLast = totalMessages - (existing?.messageCount ?: 0)
        if (!force && existing != null && sinceLast < botProperties.context.summaryRefreshEveryMessages) {
            return
        }
        if (sinceLast == 0) return

        val transcript = contextBuilder.recentTranscript(roomTarget, limit = 60)
        if (transcript.isBlank()) return

        val newSummary = generateSummary(existing?.summary, transcript) ?: return

        if (existing == null) {
            roomSummaryRepository.save(
                RoomSummaryEntity(
                    roomTarget = roomTarget,
                    summary = newSummary,
                    messageCount = totalMessages,
                    updatedAt = Instant.now(),
                ),
            )
        } else {
            existing.summary = newSummary
            existing.messageCount = totalMessages
            existing.updatedAt = Instant.now()
            roomSummaryRepository.save(existing)
        }
        LOGGER.info { "Refreshed room summary for $roomTarget ($totalMessages msgs)" }
    }

    private fun generateSummary(previousSummary: String?, transcript: String): String? {
        val maxChars = botProperties.context.maxSummaryChars
        val instruction = buildString {
            append("You maintain a running summary of a ")
            append(botProperties.persona.language)
            append(" group chat. ")
            append("Update the summary using the previous summary and the new transcript. ")
            append("Capture recurring topics, ongoing debates, who tends to argue which side, and notable facts. ")
            append("Be concise (max $maxChars characters). Write the summary in ")
            append(botProperties.persona.language)
            append(". Output only the summary text.")
        }
        val userContent = buildString {
            if (!previousSummary.isNullOrBlank()) {
                append("PREVIOUS SUMMARY:\n").append(previousSummary).append("\n\n")
            }
            append("NEW TRANSCRIPT:\n").append(transcript)
        }
        val result = llmClient.complete(
            tierName = llmProperties.gateTier,
            messages = listOf(ChatMessage.system(instruction), ChatMessage.user(userContent)),
        ) ?: return null
        return result.content.take(maxChars)
    }
}
