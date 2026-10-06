package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RoomSummaryWatermarkTest {
    private val summaries = mock(RoomSummaryRepository::class.java)
    private val history = mock(RoomSummaryHistoryRepository::class.java)
    private val messages = mock(ChatMessageRepository::class.java)
    private val context = mock(ConversationContextBuilder::class.java)
    private var result: LlmResult? = LlmResult("updated", 10)
    private var failure: RuntimeException? = null
    private val client = mock(LlmClient::class.java) { invocation ->
        if (invocation.method.name == "complete") {
            failure?.let { throw it }
            result
        } else RETURNS_DEFAULTS.answer(invocation)
    }
    private val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
    private val trace = mock(PipelineTraceService::class.java)
    private val watermark = Instant.parse("2026-09-15T00:00:00Z")
    private val existing = RoomSummaryEntity(roomTarget = "#room", summary = "old", messageCount = 1000, lastMessageReceivedAt = watermark, lastMessageId = "old")
    private val message = ChatMessageEntity(id = "new", dedupKey = "new", roomTarget = "#room", senderMemberId = 1, senderLogin = "user", messageText = "new fact", messageStyle = "message", sentAt = watermark.plusSeconds(1), receivedAt = watermark.plusSeconds(1))
    private val trigger = SummaryRefreshTrigger.Job("test")
    private val service = RoomSummaryService(summaries, history, messages, context, client, settings, trace)

    init {
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
    }

    @Test
    fun `deleted rows do not suppress a due summary refresh`() {
        service.refreshIfStale("#room", trigger)
        assertThat(existing.summary).isEqualTo("updated")
        assertThat(existing.lastMessageId).isEqualTo("new")
        assertThat(existing.lastMessageReceivedAt).isEqualTo(message.receivedAt)
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "blank", "exception"])
    fun `failed summary pauses scheduled and forced refreshes without advancing the watermark`(response: String) {
        result = null
        if (response == "blank") result = LlmResult("  \n ", 10)
        if (response == "exception") failure = IllegalStateException("provider unavailable")

        repeat(5) {
            service.refreshIfStale("#room", trigger)
            service.forceRefresh("#room", SummaryRefreshTrigger.Job("retention cleanup"))
        }

        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(1)
        val recorded = mockingDetails(trace).invocations.filter { it.method.name == "recordSummary" }
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().getArgument<String>(2)).isEqualTo("SUMMARY_FAILED")
        assertThat(recorded.single().getArgument<String>(4)).contains("automatic retries paused")
        assertThat(existing.summary).isEqualTo("old")
        assertThat(existing.messageCount).isEqualTo(1000)
        assertThat(existing.lastMessageId).isEqualTo("old")
        assertThat(existing.lastMessageReceivedAt).isEqualTo(watermark)
        assertThat(mockingDetails(summaries).invocations.map { it.method.name }).doesNotContain("save")
        verifyNoInteractions(history)
    }

    @Test
    fun `failure before the first summary is also paused`() {
        result = null
        `when`(summaries.findByRoomTarget("#room")).thenReturn(null)
        `when`(messages.countAfterWatermark("#room", Instant.EPOCH, "")).thenReturn(1)
        `when`(messages.findAfterWatermark("#room", Instant.EPOCH, "", PageRequest.of(0, 60))).thenReturn(listOf(message))

        repeat(5) { service.refreshIfStale("#room", trigger) }

        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(1)
        assertThat(mockingDetails(trace).invocations.filter { it.method.name == "recordSummary" }).hasSize(1)
        assertThat(mockingDetails(summaries).invocations.map { it.method.name }).doesNotContain("save")
    }

    @Test
    fun `changed LLM configuration resumes pending summary work`() {
        result = null
        service.refreshIfStale("#room", trigger)
        result = LlmResult("recovered", 10)
        `when`(messages.countAfterWatermark("#room", watermark, "old")).thenReturn(80)

        service.refreshIfStale("#room", trigger)
        assertThat(existing.summary).isEqualTo("old")
        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(1)

        val updatedConfiguration = mock(LlmProperties::class.java)
        `when`(updatedConfiguration.gateTier).thenReturn("gate")
        `when`(settings.llm()).thenReturn(updatedConfiguration)
        service.refreshIfStale("#room", trigger)

        assertThat(existing.summary).isEqualTo("recovered")
        assertThat(existing.lastMessageId).isEqualTo("new")
        assertThat(existing.lastMessageReceivedAt).isEqualTo(message.receivedAt)
        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(2)
        val outcomes = mockingDetails(trace).invocations.filter { it.method.name == "recordSummary" }
            .map { it.getArgument<String>(2) }
        assertThat(outcomes).containsExactly("SUMMARY_FAILED", "SUMMARY_REFRESHED")

        `when`(messages.countAfterWatermark("#room", message.receivedAt, "new")).thenReturn(40)
        `when`(messages.findAfterWatermark("#room", message.receivedAt, "new", PageRequest.of(0, 60))).thenReturn(listOf(message))
        service.refreshIfStale("#room", trigger)
        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(3)
    }

    @Test
    fun `repeated failure after configuration change pauses again`() {
        result = null
        service.refreshIfStale("#room", trigger)
        val updatedConfiguration = mock(LlmProperties::class.java)
        `when`(updatedConfiguration.gateTier).thenReturn("gate")
        `when`(settings.llm()).thenReturn(updatedConfiguration)

        repeat(5) { service.forceRefresh("#room", trigger) }

        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(2)
        val outcomes = mockingDetails(trace).invocations.filter { it.method.name == "recordSummary" }
            .map { it.getArgument<String>(2) }
        assertThat(outcomes).containsExactly("SUMMARY_FAILED", "SUMMARY_FAILED")
        assertThat(existing.lastMessageId).isEqualTo("old")
    }

    @Test
    fun `failure in one room does not pause another room`() {
        result = null
        service.refreshIfStale("#room", trigger)
        result = LlmResult("other room summary", 10)
        val otherMessage = ChatMessageEntity(id = "other", dedupKey = "other", roomTarget = "#other", senderMemberId = 1,
            senderLogin = "user", messageText = "other fact", messageStyle = "message", sentAt = Instant.now())
        `when`(messages.countByRoomTarget("#other")).thenReturn(1)
        `when`(messages.countAfterWatermark("#other", Instant.EPOCH, "")).thenReturn(1)
        `when`(messages.findAfterWatermark("#other", Instant.EPOCH, "", PageRequest.of(0, 60))).thenReturn(listOf(otherMessage))
        `when`(context.formatTranscript(listOf(otherMessage))).thenReturn("other fact")

        service.refreshIfStale("#other", trigger)
        service.refreshIfStale("#room", trigger)

        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(2)
        val saved = mockingDetails(summaries).invocations.single { it.method.name == "save" }.getArgument<RoomSummaryEntity>(0)
        assertThat(saved.roomTarget).isEqualTo("#other")
        assertThat(saved.summary).isEqualTo("other room summary")
        assertThat(existing.summary).isEqualTo("old")
    }

    @Test
    fun `concurrent scheduled and forced refreshes record one failure`() {
        result = null
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..8).map { attempt ->
                executor.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    if (attempt % 2 == 0) service.refreshIfStale("#room", trigger)
                    else service.forceRefresh("#room", trigger)
                }
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            attempts.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }

        assertThat(mockingDetails(client).invocations.filter { it.method.name == "complete" }).hasSize(1)
        assertThat(mockingDetails(trace).invocations.filter { it.method.name == "recordSummary" }).hasSize(1)
        assertThat(existing.lastMessageId).isEqualTo("old")
    }
}