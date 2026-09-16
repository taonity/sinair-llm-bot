package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.domain.PageRequest
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.client.LlmResult
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.entity.RoomSummaryEntity
import org.taonity.sinairllmbot.bot.repository.RoomSummaryHistoryRepository
import org.taonity.sinairllmbot.bot.repository.RoomSummaryRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class RoomSummaryWatermarkTest {
    @Test
    fun `deleted rows do not suppress a due summary refresh`() {
        val summaries = mock(RoomSummaryRepository::class.java)
        val history = mock(RoomSummaryHistoryRepository::class.java)
        val messages = mock(ChatMessageRepository::class.java)
        val context = mock(ConversationContextBuilder::class.java)
        val client = mock(LlmClient::class.java) { invocation ->
            if (invocation.method.name == "complete") LlmResult("updated", 10) else RETURNS_DEFAULTS.answer(invocation)
        }
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        val trace = mock(PipelineTraceService::class.java)
        val watermark = Instant.parse("2026-09-15T00:00:00Z")
        val existing = RoomSummaryEntity(roomTarget = "#room", summary = "old", messageCount = 1000, lastMessageReceivedAt = watermark, lastMessageId = "old")
        val message = ChatMessageEntity(id = "new", dedupKey = "new", roomTarget = "#room", senderMemberId = 1, senderLogin = "user", messageText = "new fact", messageStyle = "message", sentAt = watermark.plusSeconds(1), receivedAt = watermark.plusSeconds(1))
        `when`(summaries.findByRoomTarget("#room")).thenReturn(existing)
        `when`(messages.countByRoomTarget("#room")).thenReturn(50)
        `when`(messages.countAfterWatermark("#room", watermark, "old")).thenReturn(40)
        `when`(messages.findAfterWatermark("#room", watermark, "old", PageRequest.of(0, 60))).thenReturn(listOf(message))
        `when`(context.formatTranscript(listOf(message))).thenReturn("new fact")
        `when`(settings.bot().context).thenReturn(BotProperties.Context(25, 40, 2500, 6000, 1500, 45))
        `when`(settings.bot().limits).thenReturn(BotProperties.Limits(4000, 2000, 120, 9, 5))
        `when`(settings.bot().persona.language).thenReturn("Russian")
        `when`(settings.llm().gateTier).thenReturn("gate")
        `when`(history.findByRoomTargetOrderByCreatedAtDesc("#room")).thenReturn(emptyList())
        RoomSummaryService(summaries, history, messages, context, client, settings, trace)
            .refreshIfStale("#room", SummaryRefreshTrigger.Job("test"))
        assertThat(existing.summary).isEqualTo("updated")
        assertThat(existing.lastMessageId).isEqualTo("new")
        assertThat(existing.lastMessageReceivedAt).isEqualTo(message.receivedAt)
    }
}