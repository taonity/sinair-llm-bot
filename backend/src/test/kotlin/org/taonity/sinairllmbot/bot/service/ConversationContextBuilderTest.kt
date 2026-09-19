package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class ConversationContextBuilderTest {
    private val now = Instant.now()

    @Test
    fun `queued replies are visible before echo with explicit delivery state and target`() {
        val replies = OutboundStatus.entries.mapIndexed { index, status ->
            OutboundMessageEntity(id = "reply-$index", roomTarget = "#room", messageText = "answer-$index",
                status = status, createdAt = now.plusSeconds(index.toLong()), triggerMessageId = "target")
        }
        val transcript = builder(listOf(message("target", "question", now.minusSeconds(1))), replies).recentTranscript("#room")
        assertThat(transcript).contains("question", "answer-0", "answer-1", "answer-2", "reply-to:target")
            .contains("status:PENDING queued; delivery not confirmed", "status:CLAIMED claimed by collector",
                "status:SENT collector acknowledged; chat echo not yet ingested")
        assertThat(transcript.indexOf("question")).isLessThan(transcript.indexOf("answer-0"))
    }

    @Test
    fun `echo arriving during context read replaces the queued version and preserves self identity`() {
        val outbound = OutboundMessageEntity(id = "reply", roomTarget = "#room", messageText = "same answer",
            triggerMessageId = "target", createdAt = now)
        val echo = message("echo", "same answer", now.plusSeconds(1), "reply")
        val transcript = builder(listOf(echo), listOf(outbound)).recentTranscript("#room")
        assertThat(transcript.split("same answer")).hasSize(2)
        assertThat(transcript).contains("old-nickname[bot=self outbound:reply]").doesNotContain("status:PENDING")
    }

    @Test
    fun `merged history respects message bound and keeps recent human followup`() {
        val replies = listOf(OutboundMessageEntity(id = "reply", roomTarget = "#room", messageText = "own answer",
            triggerMessageId = "target", createdAt = now))
        val messages = listOf(message("followup", "continue", now.plusSeconds(1)), message("old", "old question", now.minusSeconds(1)))
        val transcript = builder(messages, replies).recentTranscript("#room", 2)
        assertThat(transcript).contains("own answer", "continue").doesNotContain("old question")
    }

    private fun message(id: String, text: String, at: Instant, outboundId: String? = null) = ChatMessageEntity(
        id = id, dedupKey = id, roomTarget = "#room", senderMemberId = 1,
        senderLogin = if (outboundId == null) "user" else "old-nickname", messageText = text,
        messageStyle = "message", sentAt = at, sourceOutboundMessageId = outboundId,
    )

    private fun builder(messages: List<ChatMessageEntity>, outbound: List<OutboundMessageEntity>): ConversationContextBuilder {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().context).thenReturn(BotProperties.Context(25, 40, 2500, 6000, 1500, 45))
        val messageRepository = mock(ChatMessageRepository::class.java) { invocation ->
            if (invocation.method.name == "findByRoomTargetOrderBySentAtDesc") messages else RETURNS_DEFAULTS.answer(invocation)
        }
        val outboundRepository = mock(OutboundMessageRepository::class.java) { invocation ->
            if (invocation.method.name == "findRecentUnechoedReplies") outbound else RETURNS_DEFAULTS.answer(invocation)
        }
        return ConversationContextBuilder(messageRepository, mock(ChatEventRepository::class.java), settings, outboundRepository)
    }
}