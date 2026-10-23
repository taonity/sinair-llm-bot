package org.taonity.sinairllmbot.bot.command

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.taonity.sinairllmbot.bot.entity.OutboundMessageEntity
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.Optional

class ChatCommandToolServiceTest {
    private val mapper = jacksonObjectMapper()
    private val tracker = mock(PipelineContextTracker::class.java)
    private val context = ToolExecutionContext("#room", "target", "segfault")
    private var saved: OutboundMessageEntity? = null
    private var savedAt: Instant? = null
    private var queriedRoom: String? = null
    private var queriedSince: Instant? = null
    private var output = listOf(systemEvent("Server response"))
    private var echo: ChatMessageEntity? = null
    private val outbound = mock(OutboundMessageRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "save" -> {
                val message: OutboundMessageEntity = invocation.getArgument(0)
                savedAt = Instant.now()
                OutboundMessageEntity(id = "outbound", roomTarget = message.roomTarget, messageText = message.messageText)
                    .also { saved = it }
            }
            "findById" -> Optional.ofNullable(saved)
            else -> RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val events = mock(ChatEventRepository::class.java) { invocation ->
        if (invocation.method.name == "findByRoomTargetAndReceivedAtAfterOrderByReceivedAtDesc") {
            queriedRoom = invocation.getArgument(0)
            queriedSince = invocation.getArgument(1)
            output
        } else RETURNS_DEFAULTS.answer(invocation)
    }
    private val messages = mock(ChatMessageRepository::class.java) { invocation ->
        if (invocation.method.name == "findBySourceOutboundMessageId") echo
        else RETURNS_DEFAULTS.answer(invocation)
    }
    private val service = ChatCommandToolService(mapper, tracker, outbound, messages, events)

    @Test
    fun `tool discovers command information through help without a command catalog`() {
        val definition = service.definitions(context).single().function!!
        val commandSchema = mapper.valueToTree<JsonNode>(definition.parameters).path("properties").path("command")

        assertThat(commandSchema.path("type").asString()).isEqualTo("string")
        assertThat(commandSchema.has("enum")).isFalse()
        assertThat(definition.description).contains("command=help", "names, syntax, effects and availability")
            .doesNotContain("/nick", "/color", "/kick", "!stop", "Bot commands", "sleep/wake")
        verifyNoInteractions(events, outbound, messages)
    }

    @Test
    fun `help returns raw system output without a locally constructed command list`() {
        output = listOf(systemEvent("New server help: /future <value>; available to this session"))

        val result = service.execute(context, "execute_chat_command", """{"command":"help"}""")

        assertThat(saved?.messageText).isEqualTo("/help")
        assertThat(result).contains("SERVER OUTPUT", output.single().eventData!!)
            .doesNotContain("Role-permitted", "Current room role", "!stop")
        assertThat(queriedRoom).isEqualTo("#room")
        assertThat(queriedSince).isBeforeOrEqualTo(savedAt!!)
    }

    @Test
    fun `commands unknown to the application are passed through with their arguments`() {
        output = listOf(systemEvent("Future action complete"))

        val result = service.execute(context, "execute_chat_command",
            """{"command":"/future-command","arguments":"a value with spaces"}""")

        assertThat(saved?.messageText).isEqualTo("/future-command a value with spaces")
        assertThat(result).contains("Future action complete")
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "not JSON", "null", "[]", "{}", "{\"command\":42}", "{\"command\":\"\"}",
        "{\"command\":\"help extra\"}", "{\"command\":\"help\",\"arguments\":42}",
        "{\"command\":\"help\",\"arguments\":\"first\\n/second\"}",
    ])
    fun `invalid tool arguments do not queue a command`(arguments: String) {
        val result = service.execute(context, "execute_chat_command", arguments)

        assertThat(result).startsWith("ERROR:")
        verifyNoInteractions(outbound, messages, events, tracker)
    }

    @Test
    @Timeout(15)
    fun `delivery and a linked command echo alone do not confirm execution`() {
        output = emptyList()
        echo = ChatMessageEntity(
            dedupKey = "echo", roomTarget = "#room", senderMemberId = 7, senderLogin = "segfault",
            messageText = "/help", messageStyle = "message", sentAt = Instant.now(), sourceOutboundMessageId = "outbound",
        )

        val result = service.execute(context, "execute_chat_command", """{"command":"help"}""")

        assertThat(result).startsWith("UNCONFIRMED:").contains("no server result", "Do not claim success")
    }

    @Test
    fun `interrupted polling retains the interrupt without claiming success or resending`() {
        output = emptyList()
        Thread.currentThread().interrupt()
        try {
            val result = service.execute(context, "execute_chat_command", """{"command":"future"}""")

            assertThat(result).startsWith("UNCONFIRMED:").contains("may still execute", "Do not repeat")
            assertThat(Thread.currentThread().isInterrupted).isTrue()
            assertThat(saved?.messageText).isEqualTo("/future")
        } finally {
            Thread.interrupted()
        }
    }

    private fun systemEvent(text: String) = ChatEventEntity(
        dedupKey = "event", roomTarget = "#room", memberId = 0, memberName = "", status = "system",
        eventData = text, eventTime = Instant.now(),
    )
}
