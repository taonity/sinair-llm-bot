package org.taonity.sinairllmbot.bot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.entity.PendingBotMessageEntity
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.service.ConversationContextBuilder
import org.taonity.sinairllmbot.bot.service.OutboundMessageService
import org.taonity.sinairllmbot.bot.service.PendingBotMessages
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.chat.service.ChatIngestService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bottest", "stub-llm")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BotConversationScenarioTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var pendingMessages: PendingBotMessages
    @Autowired private lateinit var contextBuilder: ConversationContextBuilder
    @Autowired private lateinit var chatMessages: ChatMessageRepository
    @Autowired private lateinit var outboundMessages: OutboundMessageRepository
    @Autowired private lateinit var outboundService: OutboundMessageService
    @Autowired private lateinit var ingestService: ChatIngestService
    @Autowired private lateinit var pendingRepository: PendingBotMessageRepository

    @Test
    @Transactional
    fun `restart discards old pending and unsent replies but preserves current work and sent history`() {
        val old = chatMessages.save(ChatMessageEntity(
            dedupKey = "ext:old-startup", roomTarget = "#restart", senderMemberId = 1,
            senderLogin = "user", messageText = "old question", messageStyle = "message",
            sentAt = Instant.EPOCH, receivedAt = Instant.EPOCH,
        ))
        pendingRepository.save(PendingBotMessageEntity(old.id!!, old.roomTarget, Instant.now().plusSeconds(600), Instant.EPOCH))
        val oldReply = outboundMessages.save(OutboundMessageEntity(roomTarget = old.roomTarget, messageText = "old reply", triggerMessageId = old.id, createdAt = Instant.EPOCH))
        val claimed = outboundMessages.save(OutboundMessageEntity(roomTarget = old.roomTarget, messageText = "old claimed", triggerMessageId = old.id, createdAt = Instant.EPOCH, status = OutboundStatus.CLAIMED))
        val sent = outboundMessages.save(OutboundMessageEntity(roomTarget = old.roomTarget, messageText = "sent history", createdAt = Instant.EPOCH, status = OutboundStatus.SENT))
        val current = chatMessages.save(ChatMessageEntity(
            dedupKey = "ext:new-startup", roomTarget = old.roomTarget, senderMemberId = 1,
            senderLogin = "user", messageText = "new question", messageStyle = "message", sentAt = Instant.now(),
        ))
        pendingMessages.enqueue(listOf(current), 0)

        pendingMessages.discardPreviousRun()

        assertThat(pendingRepository.existsById(old.id!!)).isFalse()
        assertThat(chatMessages.findById(old.id!!).get().botResponse?.reason).isEqualTo("RESTART")
        assertThat(outboundMessages.findById(oldReply.id!!).get().status).isEqualTo(OutboundStatus.DISCARDED)
        assertThat(outboundMessages.findById(claimed.id!!).get().status).isEqualTo(OutboundStatus.DISCARDED)
        assertThat(outboundMessages.findById(sent.id!!).get().status).isEqualTo(OutboundStatus.SENT)
        assertThat(pendingMessages.next(old.roomTarget)?.id).isEqualTo(current.id)
        assertThat(outboundService.claimPending(old.roomTarget, 10)).isEmpty()
        assertThat(contextBuilder.recentTranscript(old.roomTarget)).doesNotContain("old reply", "old claimed")
    }

    @Test
    @Transactional
    fun `stale pending and outbound work cannot escape before startup cleanup`() {
        val old = chatMessages.save(ChatMessageEntity(
            dedupKey = "ext:startup-race", roomTarget = "#startup-race", senderMemberId = 1,
            senderLogin = "user", messageText = "old question", messageStyle = "message",
            sentAt = Instant.EPOCH,
        ))
        pendingRepository.save(PendingBotMessageEntity(old.id!!, old.roomTarget, Instant.EPOCH, old.receivedAt))
        outboundMessages.save(OutboundMessageEntity(roomTarget = old.roomTarget, messageText = "stale reply", triggerMessageId = old.id))

        assertThat(pendingMessages.next(old.roomTarget)).isNull()
        assertThat(outboundService.claimPending(old.roomTarget, 10)).isEmpty()
        org.assertj.core.api.Assertions.assertThatThrownBy { pendingMessages.reply(old, "must not send") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(chatMessages.findById(old.id!!).get().botResponse?.status).isEqualTo("DISCARDED")
    }

    @Test
    @Transactional
    fun `delayed response keeps its identity through retries discard and delivery`() {
        val target = chatMessages.save(ChatMessageEntity(
            dedupKey = "ext:lifecycle", roomTarget = "#lifecycle", senderMemberId = 1,
            senderLogin = "user", messageText = "question", messageStyle = "message", sentAt = Instant.now(),
        ))
        pendingMessages.enqueue(listOf(target), 0)
        val until = Instant.now().plusSeconds(45)
        pendingMessages.defer(target, until, "WAITING_FOR_HUMANS", "Open question", "open_question")
        val firstDeferredAt = target.botResponse!!.deferredAt
        assertThat(target.botResponse!!.nextAttemptAt).isEqualTo(until)
        pendingMessages.defer(target, until, "ASSESSMENT_FAILED", "Provider unavailable")
        assertThat(target.botResponse!!.deferredAt).isEqualTo(firstDeferredAt)
        pendingMessages.finish(target, "GATE_DECLINED", "Alice already answered.", "open_question")
        assertThat(target.botResponse!!.status).isEqualTo("DISCARDED")
        assertThat(target.botResponse!!.detail).isEqualTo("Alice already answered.")
        assertThat(target.botResponse!!.nextAttemptAt).isNull()
        assertThat(pendingRepository.existsById(target.id!!)).isFalse()

        val reply = pendingMessages.reply(target, "answer")
        assertThat(target.botResponse!!.status).isEqualTo("REPLY_QUEUED")
        outboundService.claimPending(target.roomTarget, 10)
        outboundService.acknowledge(listOf(reply.id!!))
        assertThat(target.botResponse!!.status).isEqualTo("REPLIED")
        assertThat(target.botResponse!!.deferredAt).isEqualTo(firstDeferredAt)
        assertThat(target.botResponse!!.outboundMessageId).isEqualTo(reply.id)
    }

    @Test
    @Transactional
    fun `reply is visible from queue through ack and echo without duplicates or command leakage`() {
        val room = "#context-visibility"
        val target = chatMessages.save(ChatMessageEntity(
            dedupKey = "hash:context-target", roomTarget = room, senderMemberId = 1,
            senderLogin = "user", messageText = "question", messageStyle = "message", sentAt = Instant.now().minusSeconds(1),
        ))
        pendingMessages.enqueue(listOf(target), 0)
        val reply = pendingMessages.reply(target, "visible answer")
        outboundMessages.save(OutboundMessageEntity(roomTarget = room, messageText = "/msg alice private command"))
        outboundMessages.save(OutboundMessageEntity(roomTarget = "#other-room", messageText = "other room answer", triggerMessageId = target.id))
        outboundMessages.save(OutboundMessageEntity(roomTarget = room, messageText = "stale answer", triggerMessageId = target.id, createdAt = Instant.now().minusSeconds(7200)))

        assertThat(reply.triggerMessageId).isEqualTo(target.id)
        assertThat(contextBuilder.recentTranscript(room)).contains("visible answer", "status:PENDING", "reply-to:${target.id}")
            .doesNotContain("private command", "other room answer", "stale answer")
        outboundService.claimPending(room, 10)
        assertThat(contextBuilder.recentTranscript(room)).contains("status:CLAIMED")
        outboundService.acknowledge(listOf(reply.id!!))
        assertThat(contextBuilder.recentTranscript(room)).contains("status:SENT")
        ingestService.ingest(IngestRequest(listOf(ChatMessageDto(
            externalId = "context-echo", roomTarget = room, senderMemberId = 2, senderLogin = "segfault",
            senderColor = null, messageText = "visible answer", messageStyle = "message", sentAt = now(),
        ))))
        val echo = chatMessages.findBySourceOutboundMessageId(reply.id!!)
        assertThat(echo).isNotNull()
        val transcript = contextBuilder.recentTranscript(room)
        assertThat(transcript.split("visible answer")).hasSize(2)
        assertThat(transcript).contains("bot=self outbound:${reply.id}").doesNotContain("status:SENT")
    }

    @Test
    fun `bot ignores noise but replies when addressed`() {
        drainOutbound()

        ingest(
            message("aps", "pass", sentAt = now()),
        )
        Thread.sleep(2500) // past the 1s debounce + pipeline
        assertThat(claimOutbound()).describedAs("noise should not trigger a reply").isEmpty()

        val base = now()
        ingest(
            message("DJ1", "кто-нибудь поднимал nginx за последние пару лет?", sentAt = base - 20),
            message("aps", "да, недавно настраивал", sentAt = base - 10),
            message("DJ1", "@segfault что думаешь про nginx?", sentAt = base),
        )

        val replies = pollForOutbound(maxWaitMillis = 10_000)
        assertThat(replies).describedAs("addressed message should produce a reply").hasSize(1)
        assertThat(replies[0].roomTarget).isEqualTo("#chat")
        assertThat(replies[0].messageText).isEqualTo(STUB_REPLY)
    }

    private fun ingest(vararg messages: ChatMessageDto) {
        mockMvc.perform(
            post("/api/chat/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(IngestRequest(messages.toList()))),
        ).andExpect(status().isOk)
    }

    private fun pollForOutbound(maxWaitMillis: Long): List<OutboundMessageDto> {
        val deadline = System.currentTimeMillis() + maxWaitMillis
        while (System.currentTimeMillis() < deadline) {
            val claimed = claimOutbound()
            if (claimed.isNotEmpty()) return claimed
            Thread.sleep(500)
        }
        return emptyList()
    }

    private fun claimOutbound(): List<OutboundMessageDto> {
        val json = mockMvc.perform(get("/api/chat/outbound").param("limit", "20"))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return mapper.readValue(json, Array<OutboundMessageDto>::class.java).toList()
    }

    private fun drainOutbound() {
        repeat(3) { claimOutbound() }
    }

    private fun message(sender: String, text: String, sentAt: Long) = ChatMessageDto(
        externalId = "it-${idCounter++}",
        roomTarget = "#chat",
        senderMemberId = sender.hashCode() and 0xffff,
        senderLogin = sender,
        senderColor = "#ffffff",
        messageText = text,
        messageStyle = "message",
        recipientMemberId = 0,
        sentAt = sentAt,
    )

    private fun now() = Instant.now().epochSecond

    private companion object {
        // Must match wiremock/llm/mappings/llm-reply.json
        private const val STUB_REPLY = "ну такое, nginx норм, но не повод его боготворить)"
        private var idCounter = 0
        private val mapper = jacksonObjectMapper()
    }
}
