package org.taonity.sinairllmbot.bot.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.service.OutboundMessageService
import org.taonity.sinairllmbot.chat.dto.ChatMessageDto
import org.taonity.sinairllmbot.chat.dto.IngestRequest
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.chat.service.ChatIngestService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class BotMonitoringIntegrationTest {
    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var ingest: ChatIngestService
    @Autowired private lateinit var outbound: OutboundMessageService
    @Autowired private lateinit var messages: ChatMessageRepository
    @Autowired private lateinit var replies: OutboundMessageRepository
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var statusMetrics: BotStatusMetrics

    @Test
    fun `participation counts only new live human messages in bot rooms`() {
        val counter = registry.get("bot.chat.human.messages").counter()
        val before = counter.count()
        val live = message()
        val request = IngestRequest(listOf(
            live,
            message().copy(historical = true),
            message().copy(senderLogin = "segfault"),
            message().copy(roomTarget = "#unconfigured"),
        ))
        ingest.ingest(request)
        ingest.ingest(request)
        assertThat(counter.count() - before).isEqualTo(1.0)
    }

    @Test
    fun `rolled back ingestion does not increase participation`() {
        val counter = registry.get("bot.chat.human.messages").counter()
        val before = counter.count()
        val message = message()
        TransactionTemplate(transactions).executeWithoutResult { transaction ->
            ingest.ingest(IngestRequest(listOf(message)))
            transaction.setRollbackOnly()
        }
        assertThat(counter.count()).isEqualTo(before)
        assertThat(messages.existsByDedupKey("ext:${message.externalId}")).isFalse()
    }

    @Test
    fun `repeated and rolled back acknowledgments do not inflate reply counts`() {
        val trigger = messages.save(ChatMessageEntity(
            dedupKey = "metrics:${UUID.randomUUID()}", roomTarget = "#chat", senderMemberId = 1,
            senderLogin = "human", messageText = "question", messageStyle = "message",
            sentAt = Instant.now().minusSeconds(30), receivedAt = Instant.now().minusSeconds(25),
        ))
        val reply = replies.save(OutboundMessageEntity(
            roomTarget = "#chat", messageText = "answer", triggerMessageId = trigger.id,
            status = OutboundStatus.CLAIMED,
        ))
        val ids = listOf(reply.id!!)
        val counter = registry.get("bot.replies.acknowledged").counter()
        val latency = registry.get("bot.reply.ack.latency").timer()
        val before = counter.count()
        val samples = latency.count()
        TransactionTemplate(transactions).executeWithoutResult { transaction ->
            outbound.acknowledge(ids)
            transaction.setRollbackOnly()
        }
        assertThat(counter.count()).isEqualTo(before)
        assertThat(outbound.acknowledge(ids)).isEqualTo(1)
        assertThat(outbound.acknowledge(ids)).isZero()
        assertThat(counter.count() - before).isEqualTo(1.0)
        assertThat(latency.count() - samples).isEqualTo(1)
    }

    @Test
    fun `concurrent acknowledgments count each reply only once`() {
        val reply = replies.save(OutboundMessageEntity(
            roomTarget = "#chat", messageText = "concurrent answer", status = OutboundStatus.CLAIMED,
        ))
        val counter = registry.get("bot.replies.acknowledged").counter()
        val before = counter.count()
        val gate = CountDownLatch(1)
        val attempts = (1..2).map {
            CompletableFuture.supplyAsync {
                check(gate.await(10, TimeUnit.SECONDS))
                outbound.acknowledge(listOf(reply.id!!))
            }
        }
        gate.countDown()
        assertThat(attempts.sumOf { it.get(10, TimeUnit.SECONDS) }).isEqualTo(1)
        assertThat(counter.count() - before).isEqualTo(1.0)
    }

    @Test
    @Transactional
    fun `chat window counts persisted messages in order and separates rooms`() {
        messages.deleteAll()
        val start = Instant.now().plusSeconds(3600)
        fun store(index: Int, sender: String = "human", room: String = "#chat", source: String? = null) =
            messages.save(ChatMessageEntity(
                dedupKey = "window:${UUID.randomUUID()}", roomTarget = room, senderMemberId = 1,
                senderLogin = sender, messageText = "message", messageStyle = "message",
                sentAt = start.plusSeconds(index.toLong()), sourceOutboundMessageId = source,
            ))
        fun count(name: String) = registry.get(name).tags("room", "#chat", "window", "100").gauge().value()

        repeat(99) { store(it, if (it >= 95) "SeGfAuLt" else "human") }
        statusMetrics.refresh()
        assertThat(count("bot.chat.window.messages")).isEqualTo(99.0)
        assertThat(count("bot.chat.window.bot.messages")).isEqualTo(4.0)

        store(-1, "segfault")
        store(99)
        store(1000, "segfault", "#other")
        statusMetrics.refresh()
        assertThat(count("bot.chat.window.messages")).isEqualTo(100.0)
        assertThat(count("bot.chat.window.bot.messages")).isEqualTo(4.0)

        store(100, "previous-persona", source = UUID.randomUUID().toString())
        statusMetrics.refresh()
        assertThat(count("bot.chat.window.bot.messages")).isEqualTo(5.0)

        repeat(100) { store(101 + it) }
        statusMetrics.refresh()
        assertThat(count("bot.chat.window.messages")).isEqualTo(100.0)
        assertThat(count("bot.chat.window.bot.messages")).isZero()
    }

    @Test
    fun `collector heartbeat is accepted on the internal route and queue queries execute`() {
        mockMvc.perform(post("/api/chat/outbound/collector-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"rooms":["#chat"],"sendingEnabled":true}"""))
            .andExpect(status().isOk)
        assertThat(registry.get("bot.collector.joined.rooms").gauge().value()).isEqualTo(1.0)
        statusMetrics.refresh()
        assertThat(registry.get("bot.metrics.snapshot.timestamp.seconds").gauge().value()).isPositive()
    }

    private fun message() = ChatMessageDto(
        externalId = "metrics:${UUID.randomUUID()}", roomTarget = "#chat", senderMemberId = 1,
        senderLogin = "human", senderColor = null, messageText = "hello", messageStyle = "message",
        sentAt = Instant.now().epochSecond,
    )
}
