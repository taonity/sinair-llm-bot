package org.taonity.sinairllmbot.bot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

/**
 * End-to-end "few messages" scenario for the bot, exercising the full backend pipeline:
 * ingest -> debounce -> heuristic gate -> (summary/classifier) -> reply -> outbound queue.
 *
 * The LLM is faked by the `stub-llm` WireMock stub (module `llm-stubs`, mappings under
 * `wiremock/llm/mappings`), exactly like `stub-google` fakes Google OAuth. The message source is
 * the ingest endpoint — what the collector posts — so this is identical whether sinair is stub or prod.
 *
 * To run against a real provider, drop the `stub-llm` profile and set LLM_API_KEY.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("bottest", "stub-llm")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BotConversationScenarioTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `bot ignores noise but replies when addressed`() {
        drainOutbound()

        // 1) Pure noise must not trigger a reply (heuristic gate, zero tokens).
        ingest(
            message("aps", "pass", sentAt = now()),
        )
        Thread.sleep(2500) // past the 1s debounce + pipeline
        assertThat(claimOutbound()).describedAs("noise should not trigger a reply").isEmpty()

        // 2) A message addressing the bot must produce exactly one reply.
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

    // --- helpers ---

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
