package org.taonity.sinairllmbot.bot.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.common.config.AppProperties
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class BotParticipationTest {
    private val settings = mock(BotSettings::class.java)
    private val properties = mock(BotProperties::class.java)
    private var fallbackFailure: RuntimeException? = null
    private val pending = mock(PendingBotMessages::class.java) { invocation ->
        if (invocation.method.name == "reply") fallbackFailure?.let { throw it }
        RETURNS_DEFAULTS.answer(invocation)
    }
    private val triage = mock(MessageTriageService::class.java)
    private var generation = ReplyGeneration(reply = "answer")
    private var generationFailure: RuntimeException? = null
    private var canReply = false
    private val generator = mock(ReplyGenerator::class.java) { invocation ->
        if (invocation.method.name == "generateTraced") {
            generationFailure?.let { throw it }
            generation
        } else RETURNS_DEFAULTS.answer(invocation)
    }
    private val cooldown = mock(BotCooldownTracker::class.java) { invocation ->
        if (invocation.method.name == "canReply") canReply else RETURNS_DEFAULTS.answer(invocation)
    }
    private val trace = mock(PipelineTraceService::class.java)
    private val typing = mock(BotTypingService::class.java)
    private val summaries = mock(RoomSummaryService::class.java)
    private val trigger = ChatMessageEntity(id = "target", dedupKey = "ext:target", roomTarget = "#room", senderMemberId = 1, senderLogin = "user", messageText = "Question", messageStyle = "message", sentAt = Instant.now())

    private fun orchestrator(): BotMessageOrchestrator {
        `when`(settings.bot()).thenReturn(properties)
        `when`(properties.decision).thenReturn(BotProperties.Decision(0, 30, 8, 20, 45, 2, 40))
        val persona = mock(BotProperties.Persona::class.java)
        `when`(persona.name).thenReturn("segfault")
        `when`(properties.persona).thenReturn(persona)
        `when`(pending.next("#room")).thenReturn(trigger)
        return BotMessageOrchestrator(settings, mock(AppProperties::class.java), mock(BotDebouncer::class.java),
            mock(CommandGate::class.java), triage, generator, summaries, cooldown,
            mock(MutedRoomRegistry::class.java), mock(BotSleepService::class.java), typing,
            mock(RoomProcessingGuard::class.java), trace, pending)
    }

    @Test
    fun `generation can choose silence for a direct handoff without sending a failure notice`() {
        val orchestrator = orchestrator()
        canReply = true
        generation = ReplyGeneration(reply = null, suppressed = true)
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))

        orchestrator.evaluateRoom("#room")

        verify(pending).finish(trigger, "AGENT_DECLINED", "Reply agent returned no remaining contribution.", "direct_address")
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).doesNotContain("reply", "defer")
        verify(triage, never()).assess("#room", trigger, verifyStillNeeded = true)
    }

    @Test
    fun `new conversation during generation uses the freshness check before publishing`() {
        val orchestrator = orchestrator()
        canReply = true
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))
        `when`(triage.assess("#room", trigger, verifyStillNeeded = true))
            .thenReturn(TriageVerdict(false, "direct_address", "The user withdrew the request during generation."))
        `when`(pending.latestHumanMessageId("#room", "segfault")).thenReturn("before", "after")

        orchestrator.evaluateRoom("#room")

        verify(triage).assess("#room", trigger, verifyStillNeeded = true)
        verify(pending).finish(trigger, "SUPERSEDED", "The user withdrew the request during generation.", "direct_address")
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).doesNotContain("reply", "defer")
        assertStageReason("freshness", "The user withdrew the request during generation.")
    }

    @Test
    fun `freshness failure discards the draft without publishing or retrying the message`() {
        val orchestrator = orchestrator()
        canReply = true
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))
        `when`(triage.assess("#room", trigger, verifyStillNeeded = true))
            .thenThrow(IllegalStateException("freshness check produced no valid verdict"))
        `when`(pending.latestHumanMessageId("#room", "segfault")).thenReturn("before", "after")
        doAnswer {
            `when`(pending.next("#room")).thenReturn(null)
            null
        }.`when`(pending).fail(trigger, "FRESHNESS_FAILED", "freshness check produced no valid verdict")

        repeat(5) { orchestrator.evaluateRoom("#room") }

        verify(pending).fail(trigger, "FRESHNESS_FAILED", "freshness check produced no valid verdict")
        verify(triage).assess("#room", trigger, verifyStillNeeded = true)
        verify(typing).clearTyping("#room")
        verify(cooldown, never()).recordReply("#room")
        verifyNoInteractions(summaries)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name })
            .doesNotContain("reply", "defer", "finish")
        org.assertj.core.api.Assertions.assertThat(mockingDetails(generator).invocations.filter { it.method.name == "generateTraced" })
            .hasSize(1)
        val recorded = mockingDetails(trace).invocations.single { it.method.name == "record" }
        org.assertj.core.api.Assertions.assertThat(recorded.getArgument<String>(2)).isEqualTo("FAILED")
        org.assertj.core.api.Assertions.assertThat(recorded.getArgument<String?>(5)).isNull()
        val stages: List<PipelineStage> = recorded.getArgument(3)
        val failure = stages.single { it.key == "freshness_error" }
        org.assertj.core.api.Assertions.assertThat(failure.status).isEqualTo(PipelineStageStatus.STOP)
        org.assertj.core.api.Assertions.assertThat(failure.summary).isEqualTo("freshness check produced no valid verdict")
    }

    @Test
    fun `valid freshness approval allows the prepared reply to be published`() {
        val orchestrator = orchestrator()
        canReply = true
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))
        `when`(triage.assess("#room", trigger, verifyStillNeeded = true))
            .thenReturn(TriageVerdict(true, "direct_address", "The request is still unanswered."))
        `when`(pending.latestHumanMessageId("#room", "segfault")).thenReturn("before", "after")
        `when`(pending.reply(trigger, "answer"))
            .thenReturn(OutboundMessageEntity(roomTarget = "#room", messageText = "answer"))

        orchestrator.evaluateRoom("#room")

        verify(pending).reply(trigger, "answer")
        verify(cooldown).recordReply("#room")
        val recorded = mockingDetails(trace).invocations.single { it.method.name == "record" }
        org.assertj.core.api.Assertions.assertThat(recorded.getArgument<String>(2)).isEqualTo("REPLIED")
    }

    @Test
    fun `open questions wait for human answers before generating`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "open_question"))
        orchestrator.evaluateRoom("#room")
        verify(pending).defer(trigger, trigger.receivedAt.plusSeconds(45), "WAITING_FOR_HUMANS", "", "open_question")
        verifyNoInteractions(generator)
    }

    @Test
    fun `answered questions are discarded without generation`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger))
            .thenReturn(TriageVerdict(false, "open_question", "Alice already answered the room's question."))
        orchestrator.evaluateRoom("#room")
        verify(pending).finish(trigger, "GATE_DECLINED", "Alice already answered the room's question.", "open_question")
        verifyNoInteractions(generator)
        assertStageReason("triage", "Alice already answered the room's question.")
    }

    private fun assertStageReason(key: String, reason: String) {
        val recorded = mockingDetails(trace).invocations.single { it.method.name == "record" }
        val stages: List<PipelineStage> = recorded.getArgument(3)
        val stage = stages.single { it.key == key }
        org.assertj.core.api.Assertions.assertThat(stage.fields.single { it.label == "reason" }.value).isEqualTo(reason)
    }

    @Test
    fun `cooldown retains direct requests instead of discarding them`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger))
            .thenReturn(TriageVerdict(true, "direct_address", "The user asks the bot to answer."))
        orchestrator.evaluateRoom("#room")
        verify(pending, never()).finish(trigger)
        verifyNoInteractions(generator)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).contains("defer")
        assertStageReason("triage", "The user asks the bot to answer.")
    }

    @ParameterizedTest
    @ValueSource(strings = ["provider unavailable", "triage produced no valid verdict"])
    fun `assessment failure stops retries without posting an error`(detail: String) {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger)).thenThrow(IllegalStateException(detail))
        doAnswer {
            `when`(pending.next("#room")).thenReturn(null)
            null
        }.`when`(pending).fail(trigger, "ASSESSMENT_FAILED", detail)

        repeat(5) { orchestrator.evaluateRoom("#room") }

        verify(pending).fail(trigger, "ASSESSMENT_FAILED", detail)
        verify(triage).assess("#room", trigger)
        verifyNoInteractions(generator)
        verifyNoInteractions(summaries)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).doesNotContain("defer", "reply")
        val recorded = mockingDetails(trace).invocations.filter { it.method.name == "record" }
        org.assertj.core.api.Assertions.assertThat(recorded).hasSize(1)
        org.assertj.core.api.Assertions.assertThat(recorded.single().getArgument<String>(2)).isEqualTo("FAILED")
    }

    @ParameterizedTest
    @CsvSource("false,false", "false,true", "true,false", "true,true")
    fun `generation failure stops retries even when fallback cannot be queued`(throws: Boolean, fallbackFails: Boolean) {
        val orchestrator = orchestrator()
        canReply = true
        generation = ReplyGeneration(reply = null)
        if (throws) generationFailure = IllegalStateException("provider unavailable")
        if (fallbackFails) fallbackFailure = IllegalStateException("outbound unavailable")
        val detail = if (throws) "provider unavailable" else "generation produced no reply"
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))
        doAnswer {
            `when`(pending.next("#room")).thenReturn(null)
            null
        }.`when`(pending).fail(trigger, "GENERATION_FAILED", detail)

        repeat(5) { orchestrator.evaluateRoom("#room") }

        verify(pending).fail(trigger, "GENERATION_FAILED", detail)
        verify(triage).assess("#room", trigger)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(generator).invocations.filter { it.method.name == "generateTraced" }).hasSize(1)
        val recorded = mockingDetails(trace).invocations.filter { it.method.name == "record" }
        org.assertj.core.api.Assertions.assertThat(recorded).hasSize(1)
        org.assertj.core.api.Assertions.assertThat(recorded.single().getArgument<String>(2)).isEqualTo("FAILED")
        val queued = mockingDetails(pending).invocations.filter { it.method.name == "reply" }
        org.assertj.core.api.Assertions.assertThat(queued).hasSize(1)
        org.assertj.core.api.Assertions.assertThat(queued.single().getArgument<Boolean>(2)).isTrue()
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).doesNotContain("defer")
        val cooldownRooms = mockingDetails(cooldown).invocations
            .filter { it.method.name == "recordReply" }
            .map { it.getArgument<String>(0) }
        org.assertj.core.api.Assertions.assertThat(cooldownRooms)
            .containsExactlyElementsOf(if (fallbackFails) emptyList() else listOf("#room"))
    }
}
