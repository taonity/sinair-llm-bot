package org.taonity.sinairllmbot.console

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.taonity.sinairllmbot.other.ControllerTestsBaseClass
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.command.ChatCommandToolService
import org.taonity.sinairllmbot.bot.service.PipelineTraceService
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.chat.entity.BotResponseState
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant

@DirtiesContext
class ConsoleAccessControllerTest : ControllerTestsBaseClass() {

    @Autowired
    private lateinit var pipelineRunRepository: PipelineRunRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    private lateinit var outboundMessageRepository: OutboundMessageRepository

    @Autowired
    private lateinit var pipelineTraceService: PipelineTraceService

    @Autowired
    private lateinit var commandToolService: ChatCommandToolService

    @Test
    fun `legacy failure notices resolve only an explicit pipeline URL for their own trigger`() {
        val session = authorizeOAuth2()
        val room = "#legacy-failure-link"
        val run = pipelineRunRepository.save(PipelineRunEntity(
            pipelineKey = "reply", roomTarget = room, triggerMessageId = "legacy-trigger", triggerSenderLogin = "alice",
            triggerText = "question", outcome = "FAILED", stagesJson = "[]",
        ))
        val notice = outboundMessageRepository.save(OutboundMessageEntity(
            roomTarget = room, messageText = "Failure notice: https://console.test/?pipeline=${run.id}", triggerMessageId = "legacy-trigger",
        ))
        val unrelated = outboundMessageRepository.save(OutboundMessageEntity(
            roomTarget = room, messageText = "Unrelated https://console.test/?pipeline=${run.id}", triggerMessageId = "other-trigger",
        ))

        mockMvc.perform(get("/console/outbound-messages").param("q", notice.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(run.id))
        mockMvc.perform(get("/console/outbound-messages").param("q", unrelated.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `commands and failure notices link to their exact pipeline`() {
        val session = authorizeOAuth2()
        val trigger = ChatMessageEntity(
            roomTarget = "#command-pipeline", dedupKey = "ext:command-pipeline", senderMemberId = 1,
            senderLogin = "alice", messageText = "change color", messageStyle = "message", sentAt = Instant.now(),
        )
        pipelineTraceService.begin()
        val commandId = commandToolService.persistOutbound(trigger.roomTarget, "/color red", "color", "red")
        val runId = pipelineTraceService.record("reply", trigger, "FAILED", emptyList())!!
        val notice = outboundMessageRepository.save(OutboundMessageEntity(
            roomTarget = trigger.roomTarget, messageText = "Failed to reply",
        ))
        pipelineTraceService.linkOutbound(runId, listOf(notice.id!!))

        org.assertj.core.api.Assertions.assertThat(outboundMessageRepository.findById(commandId).get().pipelineRunId).isEqualTo(runId)
        mockMvc.perform(get("/console/outbound-messages").param("room", trigger.roomTarget).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(runId))
            .andExpect(jsonPath("$.content[1].pipelineRunId").value(runId))

        pipelineRunRepository.deleteById(runId)
        mockMvc.perform(get("/console/outbound-messages").param("room", trigger.roomTarget).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.content[1].pipelineRunId").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `outbound rows link to their pipeline only while the trace exists`() {
        val session = authorizeOAuth2()
        val outbound = outboundMessageRepository.save(OutboundMessageEntity(
            roomTarget = "#outbound-pipeline", messageText = "outbound-pipeline-answer",
        ))
        val run = pipelineRunRepository.save(PipelineRunEntity(
            pipelineKey = "reply", roomTarget = outbound.roomTarget, triggerSenderLogin = "alice",
            triggerText = "question", outcome = "REPLIED", stagesJson = "[]", outboundMessageId = outbound.id,
        ))

        mockMvc.perform(get("/console/outbound-messages").param("q", outbound.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(run.id))

        pipelineRunRepository.deleteById(run.id!!)
        mockMvc.perform(get("/console/outbound-messages").param("q", outbound.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].pipelineRunId").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `message and earlier pipeline attempts expose the latest delayed response outcome`() {
        val session = authorizeOAuth2()
        val message = chatMessageRepository.save(ChatMessageEntity(
            dedupKey = "ext:delayed-console", roomTarget = "#delayed-console", senderMemberId = 1,
            senderLogin = "alice", messageText = "delayed-console-question", messageStyle = "message", sentAt = Instant.now(),
            botResponse = BotResponseState("DEFERRED", "WAITING_FOR_HUMANS", deferredAt = Instant.now()),
        ))
        pipelineRunRepository.save(PipelineRunEntity(
            pipelineKey = "reply", roomTarget = message.roomTarget, triggerMessageId = message.id,
            triggerSenderLogin = message.senderLogin, triggerText = message.messageText,
            outcome = "DEFERRED", stagesJson = "[]",
        ))
        message.botResponse = message.botResponse!!.copy(status = "DISCARDED", reason = "GATE_DECLINED", detail = "Bob already answered.")
        chatMessageRepository.save(message)

        mockMvc.perform(get("/console/chat-messages").param("q", message.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].botResponse.status").value("DISCARDED"))
            .andExpect(jsonPath("$.content[0].botResponse.deferredAt").isNotEmpty)
        mockMvc.perform(get("/console/pipeline-runs").param("q", message.messageText).cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].outcome").value("DEFERRED"))
            .andExpect(jsonPath("$.content[0].botResponse.status").value("DISCARDED"))
            .andExpect(jsonPath("$.content[0].botResponse.detail").value("Bob already answered."))
    }

    @Test
    fun `access endpoint requires authentication`() {
        mockMvc.perform(get("/console/access/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrapped owner sees owner access`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/access/me").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.canView").value(true))
            .andExpect(jsonPath("$.canEdit").value(true))
            .andExpect(jsonPath("$.isAdmin").value(true))
            .andExpect(jsonPath("$.isOwner").value(true))
    }

    @Test
    fun `admin can list chat messages and audit logs`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/chat-messages").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mockMvc.perform(get("/console/audit-logs").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `console endpoints reject anonymous access`() {
        mockMvc.perform(get("/console/chat-messages"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `owner can list users including self`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/users").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[?(@.email == 'test@example.com')].role").value("OWNER"))
    }

    @Test
    fun `viewer can export complete pipeline debug bundle`() {
        val session = authorizeOAuth2()
        val run = pipelineRunRepository.save(
            PipelineRunEntity(
                pipelineKey = "reply",
                roomTarget = "room-1",
                triggerSenderLogin = "alice",
                triggerText = "Why did this fail?",
                outcome = "FAILED",
                outcomeDetail = "model error",
                stagesJson = """[{"key":"triage","label":"Triage","status":"STOP","summary":"failed","fields":[],"alternatives":[]}]""",
                totalTokens = 12,
                llmUsageJson = """[{"tier":"gate","model":"test/model","tokens":12,"requestPayload":"{\"prompt\":\"hello\"}","responsePayload":"{\"error\":\"bad\"}"}]""",
                jsonParseFailureCount = 1,
                jsonParseFailuresJson = """[{"label":"triage","attempt":1,"payload":"not json"}]""",
                configRevisionId = "revision-1",
                contextManifestJson = """{"sources":["docs/TESTING.md"]}""",
            ),
        )

        mockMvc.perform(get("/console/pipeline-runs/${run.id}/export").cookie(session))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pipeline-${run.id}.md\""))
            .andExpect(content().contentType("text/markdown;charset=UTF-8"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Pipeline debug bundle")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Why did this fail?")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("docs/TESTING.md")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"prompt\" : \"hello\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"error\" : \"bad\"")))

        mockMvc.perform(get("/console/pipeline-runs/${run.id}/export"))
            .andExpect(status().isUnauthorized)
    }
}
