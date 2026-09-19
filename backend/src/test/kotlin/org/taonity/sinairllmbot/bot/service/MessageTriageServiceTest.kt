package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.taonity.sinairllmbot.bot.client.ChatMessage
import org.taonity.sinairllmbot.bot.client.LlmClient
import org.taonity.sinairllmbot.bot.client.LlmResult
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailureTracker
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

class MessageTriageServiceTest {
    @Test
    fun `leading current nickname or alias reaches generation without a conservative gate`() {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().persona.name).thenReturn("сега")
        `when`(settings.bot().persona.aliases).thenReturn(listOf("segfault"))
        val context = mock(ConversationContextBuilder::class.java)
        val client = mock(LlmClient::class.java)
        val runner = mock(JsonPromptRunner::class.java)
        val service = MessageTriageService(client, context, settings, jacksonObjectMapper(), runner)

        for (text in listOf("@сега отвечай", "  @СЕГА, продолжай", "@segfault retry", "@сега")) {
            assertThat(service.assess("#room", target(text)))
                .isEqualTo(TriageVerdict(true, "direct_address"))
        }
        `when`(settings.bot().persona.name).thenReturn("новый-ник")
        assertThat(service.assess("#room", target("@новый-ник отвечай"))).isEqualTo(TriageVerdict(true, "direct_address"))
        verifyNoInteractions(client, context, runner)
    }

    @Test
    fun `quoted mentions other recipients and longer names still get classified`() {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().persona.name).thenReturn("сега")
        `when`(settings.bot().persona.aliases).thenReturn(emptyList())
        `when`(settings.bot().persona.language).thenReturn("Russian")
        `when`(settings.llm().gateTier).thenReturn("gate")
        `when`(settings.llm().jsonRetryAttempts).thenReturn(1)
        val context = mock(ConversationContextBuilder::class.java)
        `when`(context.recentTranscript("#room", 25)).thenReturn("")
        val prompts = mutableListOf<String>()
        val client = mock(LlmClient::class.java) { invocation ->
            if (invocation.method.name == "complete") {
                val messages: List<ChatMessage> = invocation.getArgument(1)
                prompts += messages.first().content.toString()
                LlmResult("""{"respond":false,"category":"not_addressed"}""", 10)
            } else RETURNS_DEFAULTS.answer(invocation)
        }
        val service = MessageTriageService(client, context, settings, jacksonObjectMapper(),
            JsonPromptRunner(settings, mock(JsonParseFailureTracker::class.java)))
        for (text in listOf("@alice спроси @сега", "> @сега отвечай", "\"@сега отвечай\"", "@сегатор отвечай")) {
            assertThat(service.assess("#room", target(text)).respond).isFalse()
        }
        assertThat(prompts).hasSize(4)
        assertThat(service.assess("#room", target("@сега отвечай"), verifyStillNeeded = true).respond).isFalse()
        assertThat(prompts.last()).contains("This is a freshness check", "Unrelated later chatter does not cancel it")
    }

    private fun target(text: String) = ChatMessageEntity(
        id = "target", dedupKey = "ext:target", roomTarget = "#room", senderMemberId = 1,
        senderLogin = "user", messageText = text, messageStyle = "message", sentAt = Instant.EPOCH,
    )

    @Test
    fun `recipient exclusion precedes capability and correction rules even after waiting`() {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().persona.name).thenReturn("segfault")
        `when`(settings.bot().persona.aliases).thenReturn(emptyList())
        `when`(settings.bot().persona.language).thenReturn("Russian")
        `when`(settings.llm().gateTier).thenReturn("gate")
        `when`(settings.llm().jsonRetryAttempts).thenReturn(1)
        val context = mock(ConversationContextBuilder::class.java)
        `when`(context.recentTranscript("#room", 25)).thenReturn("user: @alice, why did segfault say that?\nother: unrelated chatter")
        var sentMessages = emptyList<ChatMessage>()
        val client = mock(LlmClient::class.java) { invocation ->
            if (invocation.method.name == "complete") {
                sentMessages = invocation.getArgument(1)
                LlmResult("""{"respond":false,"category":"not_addressed"}""", 10)
            } else RETURNS_DEFAULTS.answer(invocation)
        }
        val target = ChatMessageEntity(
            id = "target", dedupKey = "ext:target", roomTarget = "#room", senderMemberId = 1,
            senderLogin = "user", messageText = "@alice, why did segfault say that?", messageStyle = "message",
            sentAt = Instant.EPOCH, receivedAt = Instant.EPOCH,
        )
        val service = MessageTriageService(client, context, settings, jacksonObjectMapper(),
            JsonPromptRunner(settings, mock(JsonParseFailureTracker::class.java)))

        val verdict = service.assess("#room", target)

        assertThat(verdict.respond).isFalse()
        assertThat(verdict.loggableCategory).isEqualTo("not_addressed")
        val system = sentMessages.first().content.toString()
        assertThat(system).contains(
            "takes precedence over every positive rule",
            "unless the bot is ALSO explicitly invited",
            "absence of a human answer does not turn a person-directed question into an open question",
            "Capabilities describe what the bot can do, not who a request addresses",
            "may fit an active exchange WITH the bot without adding factual information",
            "This never permits interrupting other people's exchanges",
        )
        assertThat(system.indexOf("RECIPIENT EXCLUSION")).isLessThan(system.indexOf("1) respond"))
        assertThat(sentMessages[1].content.toString()).contains("The TARGET contains").doesNotContain("latest message above")
        assertThat(sentMessages.last().content.toString()).contains("TARGET id=target", target.messageText)
    }
}