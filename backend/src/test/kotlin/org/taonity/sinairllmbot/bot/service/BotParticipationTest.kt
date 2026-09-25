package org.taonity.sinairllmbot.bot.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.common.config.AppProperties
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class BotParticipationTest {
    private val settings = mock(BotSettings::class.java)
    private val properties = mock(BotProperties::class.java)
    private val pending = mock(PendingBotMessages::class.java)
    private val triage = mock(MessageTriageService::class.java)
    private var generation = ReplyGeneration(reply = "answer")
    private var canReply = false
    private val generator = mock(ReplyGenerator::class.java) { invocation ->
        if (invocation.method.name == "generateTraced") generation else RETURNS_DEFAULTS.answer(invocation)
    }
    private val cooldown = mock(BotCooldownTracker::class.java) { invocation ->
        if (invocation.method.name == "canReply") canReply else RETURNS_DEFAULTS.answer(invocation)
    }
    private val trace = mock(PipelineTraceService::class.java)
    private val trigger = ChatMessageEntity(id = "target", dedupKey = "ext:target", roomTarget = "#room", senderMemberId = 1, senderLogin = "user", messageText = "Question", messageStyle = "message", sentAt = Instant.now())

    private fun orchestrator(): BotMessageOrchestrator {
        `when`(settings.bot()).thenReturn(properties)
        `when`(properties.decision).thenReturn(BotProperties.Decision(0, 30, 8, 20, 45, 2, 40))
        val persona = mock(BotProperties.Persona::class.java)
        `when`(persona.name).thenReturn("segfault")
        `when`(properties.persona).thenReturn(persona)
        `when`(pending.next("#room")).thenReturn(trigger)
        return BotMessageOrchestrator(settings, mock(AppProperties::class.java), mock(BotDebouncer::class.java),
            mock(CommandGate::class.java), triage, generator, mock(RoomSummaryService::class.java), cooldown,
            mock(MutedRoomRegistry::class.java), mock(BotSleepService::class.java), mock(BotTypingService::class.java),
            mock(RoomProcessingGuard::class.java), trace, pending)
    }

    @Test
    fun `generation can choose silence for a direct handoff without sending a failure notice`() {
        val orchestrator = orchestrator()
        canReply = true
        generation = ReplyGeneration(reply = null, suppressed = true)
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "direct_address"))

        orchestrator.evaluateRoom("#room")

        verify(pending).finish(trigger)
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
        verify(pending).finish(trigger)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).doesNotContain("reply", "defer")
        assertStageReason("freshness", "The user withdrew the request during generation.")
    }

    @Test
    fun `open questions wait for human answers before generating`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger)).thenReturn(TriageVerdict(true, "open_question"))
        orchestrator.evaluateRoom("#room")
        verify(pending).defer(trigger, trigger.receivedAt.plusSeconds(45))
        verifyNoInteractions(generator)
    }

    @Test
    fun `answered questions are discarded without generation`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger))
            .thenReturn(TriageVerdict(false, "open_question", "Alice already answered the room's question."))
        orchestrator.evaluateRoom("#room")
        verify(pending).finish(trigger)
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

    @Test
    fun `assessment failure retains the request without posting an error`() {
        val orchestrator = orchestrator()
        `when`(triage.assess("#room", trigger)).thenThrow(IllegalStateException("provider unavailable"))
        orchestrator.evaluateRoom("#room")
        verifyNoInteractions(generator)
        verify(pending, never()).finish(trigger)
        org.assertj.core.api.Assertions.assertThat(mockingDetails(pending).invocations.map { it.method.name }).contains("defer").doesNotContain("reply")
    }
}