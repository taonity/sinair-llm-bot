package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
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
            val verdict = service.assess("#room", target(text))
            assertThat(verdict.respond).isTrue()
            assertThat(verdict.category).isEqualTo("direct_address")
            assertThat(verdict.reason).contains("Direct-mention handoff rule:", "the target starts with a bot mention", "without an LLM gate decision")
        }
        `when`(settings.bot().persona.name).thenReturn("новый-ник")
        assertThat(service.assess("#room", target("@новый-ник отвечай")).category).isEqualTo("direct_address")
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
                LlmResult("""{"respond":false,"category":"not_addressed","reason":"Exclusive-recipient rule: the message addresses another participant."}""", 10)
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

    @Test
    fun `reason is retained for both outcomes and freshness checks`() {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().persona.name).thenReturn("segfault")
        `when`(settings.bot().persona.aliases).thenReturn(emptyList())
        `when`(settings.bot().persona.language).thenReturn("English")
        `when`(settings.llm().gateTier).thenReturn("gate")
        `when`(settings.llm().jsonRetryAttempts).thenReturn(1)
        val context = mock(ConversationContextBuilder::class.java)
        `when`(context.recentTranscript("#room", 25)).thenReturn("")
        var response = ""
        var system = ""
        val client = mock(LlmClient::class.java) { invocation ->
            if (invocation.method.name == "complete") {
                val messages: List<ChatMessage> = invocation.getArgument(1)
                system = messages.first().content.toString()
                LlmResult(response, 10)
            } else RETURNS_DEFAULTS.answer(invocation)
        }
        val service = MessageTriageService(client, context, settings, jacksonObjectMapper(),
            JsonPromptRunner(settings, mock(JsonParseFailureTracker::class.java)))

        for (verifyStillNeeded in listOf(false, true)) {
            for (respond in listOf(true, false)) {
                val reason = if (respond) "Open-question rule: the room's question is still unanswered."
                    else "Resolution rule: Alice already answered the room's question."
                response = """{"respond":$respond,"category":"open_question","reason":"$reason"}"""
                val verdict = service.assess("#room", target("How does this work?"), verifyStillNeeded)
                assertThat(verdict.respond).isEqualTo(respond)
                assertThat(verdict.reason).isEqualTo(reason)
                assertThat(system).contains(
                    "BOTH respond=true and respond=false, including freshness checks",
                    "Decisive prompt rule: concrete recipient or contextual evidence",
                    "Name the main rule from this prompt that determines the outcome",
                    "name that overriding rule and its evidence",
                    "Use only evidence in the supplied messages",
                    "respond=true: 'Direct-address rule:",
                    "respond=false: 'Resolution rule:",
                )
            }
        }
        assertThat(system).contains("\"reason\": string", "at most 30 words", "Always provide a nonempty reason")
    }

    @ParameterizedTest
    @CsvSource("true,false", "false,false", "true,true", "false,true")
    fun `malformed verdict retries and uses the complete replacement`(respond: Boolean, freshness: Boolean) {
        val failures = JsonParseFailureTracker()
        failures.begin()
        var calls = 0
        val service = service({
            calls++
            if (calls == 1) """{"respond":true,"reason":"unfinished"""
            else """{"respond":$respond,"category":"open_question","reason":"Open-question rule: the room asks for help."}"""
        }, failures)

        val verdict = service.assess("#room", target("How does this work?"), freshness)

        assertThat(verdict).isEqualTo(TriageVerdict(respond, "open_question", "Open-question rule: the room asks for help."))
        assertThat(calls).isEqualTo(2)
        val recorded = failures.drain()
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().label).isEqualTo(if (freshness) "freshness" else "triage")
        assertThat(recorded.single().attempt).isEqualTo(1)
        assertThat(recorded.single().payload).contains("unfinished")
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "",
        "null",
        "[]",
        "{}",
        """{"respond":true,"category":"open_question","reason":"unfinished""",
        """{"respond":false,"category":"noise","reason":"unfinished""",
        """{"category":"open_question","reason":"Missing decision"}""",
        """{"respond":null,"category":"open_question","reason":"Null decision"}""",
        """{"respond":"true","category":"open_question","reason":"String decision"}""",
        """{"respond":1,"category":"open_question","reason":"Numeric decision"}""",
        """{"respond":true,"reason":"Missing category"}""",
        """{"respond":true,"category":null,"reason":"Null category"}""",
        """{"respond":true,"category":1,"reason":"Numeric category"}""",
        """{"respond":true,"category":"unknown","reason":"Unknown category"}""",
        """{"respond":true,"category":"","reason":"Empty category"}""",
        """{"respond":true,"category":"open_question"}""",
        """{"respond":false,"category":"noise"}""",
        """{"respond":true,"category":"open_question","reason":null}""",
        """{"respond":true,"category":"open_question","reason":42}""",
        """{"respond":true,"category":"open_question","reason":""}""",
        """{"respond":true,"category":"open_question","reason":"   "}""",
        """{"respond":true,"category":"open_question","reason":"Complete"} {"respond":false}""",
    ])
    fun `invalid verdicts exhaust the retry budget without returning a decision`(response: String) {
        for (freshness in listOf(false, true)) {
            val failures = JsonParseFailureTracker()
            failures.begin()
            var calls = 0
            val service = service({ calls++; response }, failures)

            assertThatThrownBy { service.assess("#room", target("How does this work?"), freshness) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("${if (freshness) "freshness check" else "triage"} produced no valid verdict")

            assertThat(calls).isEqualTo(3)
            val recorded = failures.drain()
            assertThat(recorded.map { it.attempt }).containsExactly(1, 2, 3)
            assertThat(recorded.map { it.label }).containsOnly(if (freshness) "freshness" else "triage")
            assertThat(recorded.map { it.payload }).containsOnly(response)
        }
    }

    private fun service(response: () -> String, failures: JsonParseFailureTracker): MessageTriageService {
        val settings = mock(BotSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.bot().persona.name).thenReturn("segfault")
        `when`(settings.bot().persona.aliases).thenReturn(emptyList())
        `when`(settings.bot().persona.language).thenReturn("English")
        `when`(settings.llm().gateTier).thenReturn("gate")
        `when`(settings.llm().jsonRetryAttempts).thenReturn(3)
        val context = mock(ConversationContextBuilder::class.java)
        `when`(context.recentTranscript("#room", 25)).thenReturn("")
        val client = mock(LlmClient::class.java) { invocation ->
            if (invocation.method.name == "complete") LlmResult(response(), 10)
            else RETURNS_DEFAULTS.answer(invocation)
        }
        return MessageTriageService(client, context, settings, jacksonObjectMapper(), JsonPromptRunner(settings, failures))
    }

    private fun target(text: String) = ChatMessageEntity(
        id = "target", dedupKey = "ext:target", roomTarget = "#room", senderMemberId = 1,
        senderLogin = "user", messageText = text, messageStyle = "message", sentAt = Instant.EPOCH,
    )

    @Test
    fun `gate preserves bot requests and selects open questions by concrete advantage`() {
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
                LlmResult("""{"respond":false,"category":"not_addressed","reason":"Exclusive-recipient rule: the question addresses Alice."}""", 10)
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
            "Exclude requests clearly addressed ONLY to another participant",
            "If the bot is also addressed, it must respond",
            "absence of a human answer does not turn a person-directed question into an open question",
            "Capabilities describe what the bot can do, not who a request addresses",
            "with or without @, anywhere in the message",
            "If a bot-name occurrence is ambiguous, prefer direct_address with respond=true",
            "greetings and jokes",
            "open_question: default respond=false",
            "a source lookup, calculation or code diagnosis gives the bot a clear advantage",
            "name that advantage in the reason",
            "questions already answered or being handled",
            "respond=true only for a clear, material and checkable factual",
            "Minor inaccuracies, pedantic corrections",
            "When unsure, respond=false",
            "Reserve unsolicited replies for material misinformation",
            "thanks addressed to the bot",
            "Never use noise for a genuine question",
            "'segfault, why did @alice say that?' -> respond=true, direct_address",
            "'@alice, why did segfault say that?' -> respond=false, not_addressed",
            "'any recommendations' or 'what do you all think?' to the room -> respond=false, open_question",
            "'yes, do it' accepting the bot's offer -> respond=true, indirect_address",
        )
        assertThat(system).doesNotContain("to be helpful, or to seem present", "A mention is not an invitation")
        assertThat(sentMessages[1].content.toString())
            .contains("The TARGET contains", "Treat it as direct address unless context clearly shows")
            .doesNotContain("latest message above", "Apply the recipient exclusion first")
        assertThat(sentMessages.last().content.toString()).contains("TARGET id=target", target.messageText)
    }
}
