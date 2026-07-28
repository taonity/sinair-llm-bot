package org.taonity.sinairllmbot.bot.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.chat.service.ChatIngestService
import org.taonity.sinairllmbot.config.service.ConfigRevisionService
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@SpringBootTest
@ActiveProfiles("bottest")
@Transactional
class ApplicationContextToolServiceTest {
    @Autowired
    private lateinit var tools: ApplicationContextToolService

    @Autowired
    private lateinit var messages: ChatMessageRepository

    @Autowired
    private lateinit var pipelines: PipelineRunRepository

    @Autowired
    private lateinit var outbound: OutboundMessageRepository

    @Autowired
    private lateinit var ingestService: ChatIngestService

    @Autowired
    private lateinit var revisions: ConfigRevisionService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `config search returns effective allowlisted value and no infrastructure secrets`() {
        val trigger = saveMessage("#chat", "alice", "@segfault what is your cooldown?", 20)
        val result = execute(
            trigger,
            "search_app_context",
            mapOf("query" to "cooldown config", "types" to listOf("config")),
        )

        assertThat(result).contains("app.bot.decision.cooldown-seconds")
        assertThat(result).contains("effective=1")
        assertThat(result).doesNotContain("api-key", "base-url", "password")
    }

    @Test
    fun `a context URI from another room is indistinguishable from missing`() {
        val trigger = saveMessage("#chat", "alice", "@segfault inspect this", 20)
        val other = saveMessage("#private", "mallory", "other-room-secret", 10)
        val result = execute(
            trigger,
            "get_app_context",
            mapOf("uris" to listOf("message://chat/${other.id}"), "detail" to "diagnostic"),
        )

        assertThat(result).contains("not found or unavailable in this room")
        assertThat(result).doesNotContain("other-room-secret", "#private")
    }

    @Test
    fun `previous user message resolves its exact persisted pipeline and config revision`() {
        val previous = saveMessage("#chat", "alice", "@segfault why were you silent?", 10)
        val trigger = saveMessage("#chat", "alice", "@segfault what pipeline did that take?", 20)
        val revisionId = revisions.currentRevisionId()
        pipelines.saveAndFlush(
            PipelineRunEntity(
                pipelineKey = "reply",
                roomTarget = "#chat",
                triggerMessageId = previous.id,
                triggerSenderLogin = previous.senderLogin,
                triggerText = previous.messageText,
                outcome = "COOLDOWN",
                stagesJson = """[{"key":"cooldown","label":"Cooldown","status":"STOP","summary":"on cooldown"}]""",
                configRevisionId = revisionId,
            ),
        )

        val result = execute(
            trigger,
            "get_message_pipeline",
            mapOf("message" to "previous_user", "detail" to "normal"),
        )

        assertThat(result).contains("why were you silent")
        assertThat(result).contains("\"outcome\":\"COOLDOWN\"")
        assertThat(result).contains("config://revision/$revisionId")
        assertThat(result).doesNotContain("what pipeline did that take")
    }

    @Test
    fun `current trigger exposes completed live stages without claiming a final outcome`() {
        val trigger = saveMessage("#chat", "alice", "@segfault why are you responding?", 20)
        val context = ToolExecutionContext(
            roomTarget = "#chat",
            triggerMessageId = trigger.id!!,
            botName = "segfault",
            completedStages = listOf(
                org.taonity.sinairllmbot.bot.pipeline.PipelineStage(
                    key = "triage",
                    label = "Triage",
                    status = org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus.OK,
                    summary = "respond=true",
                ),
            ),
        )
        val result = tools.execute(
            context,
            "get_message_pipeline",
            objectMapper.writeValueAsString(mapOf("message" to "trigger")),
        )

        assertThat(result).contains("\"status\":\"RUNNING\"")
        assertThat(result).contains("\"finalOutcomeAvailable\":false")
        assertThat(result).contains("respond=true")
    }

    @Test
    fun `bot self echo is linked to the closest outbound row with explicit heuristic provenance`() {
        val sentAt = Instant.parse("2026-01-01T00:00:10Z")
        val queued = outbound.saveAndFlush(
            OutboundMessageEntity(
                roomTarget = "#chat",
                messageText = "a reconciled bot reply",
                status = OutboundStatus.CLAIMED,
                createdAt = sentAt.minusSeconds(2),
                claimedAt = sentAt.minusSeconds(1),
            ),
        )
        ingestService.ingest(
            IngestRequest(
                messages = listOf(
                    ChatMessageDto(
                        externalId = "context-self-echo",
                        roomTarget = "#chat",
                        senderMemberId = 42,
                        senderLogin = "segfault",
                        senderColor = null,
                        messageText = queued.messageText,
                        messageStyle = "message",
                        sentAt = sentAt.epochSecond,
                        historical = true,
                    ),
                ),
            ),
        )

        val echo = messages.findByRoomTargetOrderBySentAtDesc("#chat", org.springframework.data.domain.PageRequest.of(0, 10))
            .first { it.messageText == queued.messageText }
        assertThat(echo.sourceOutboundMessageId).isEqualTo(queued.id)
        assertThat(echo.sourceOutboundMatch).isEqualTo("ECHO_TIME_TEXT")
    }

    private fun execute(trigger: ChatMessageEntity, name: String, args: Map<String, Any?>): String =
        tools.execute(
            ToolExecutionContext(
                roomTarget = trigger.roomTarget,
                triggerMessageId = trigger.id!!,
                botName = "segfault",
            ),
            name,
            objectMapper.writeValueAsString(args),
        )

    private fun saveMessage(room: String, sender: String, text: String, seconds: Long): ChatMessageEntity =
        messages.saveAndFlush(
            ChatMessageEntity(
                dedupKey = "context-test-$room-$sender-$seconds-${text.hashCode()}",
                roomTarget = room,
                senderMemberId = sender.hashCode(),
                senderLogin = sender,
                messageText = text,
                messageStyle = "message",
                sentAt = Instant.ofEpochSecond(seconds),
            ),
        )
}
