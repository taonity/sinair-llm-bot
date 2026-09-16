package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import org.springframework.core.io.ClassPathResource
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.config.GithubProperties
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.config.Prompt
import org.taonity.sinairllmbot.bot.grafana.GrafanaMcpProperties
import org.taonity.sinairllmbot.bot.ingestion.ContextBuilder
import org.taonity.sinairllmbot.bot.ingestion.SourceIngestionService
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatEventRepository
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import org.taonity.sinairllmbot.common.config.AppProperties
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class ReplyPromptBuilderTest {
    @Test
    fun `includes operator UI and technical request guidance`() {
        val roomSummaryService = mock(RoomSummaryService::class.java)
        val settings = mock(BotSettings::class.java)
        val sourceIngestionService = mock(SourceIngestionService::class.java)
        val ingestionContextBuilder = mock(ContextBuilder::class.java)
        val botProperties = mock(BotProperties::class.java)
        val llmProperties = mock(LlmProperties::class.java)
        val githubProperties = mock(GithubProperties::class.java)
        val chatMessageRepository = mock(ChatMessageRepository::class.java)
        val chatEventRepository = mock(ChatEventRepository::class.java)
        val contextBuilder = ConversationContextBuilder(chatMessageRepository, chatEventRepository, settings)

        `when`(settings.bot()).thenReturn(botProperties)
        `when`(settings.llm()).thenReturn(llmProperties)
        `when`(settings.ingestion()).thenReturn(IngestionProperties())
        `when`(settings.github()).thenReturn(githubProperties)
        `when`(botProperties.persona).thenReturn(
            BotProperties.Persona(
                name = "segfault",
                language = "Russian",
                prompt = Prompt(ClassPathResource("prompts/persona.txt").inputStream.bufferedReader().use { it.readText() }),
                creatorUserId = 0,
                stopCommand = "!stop",
                startCommand = "!start",
                sleepCommand = "!sleep",
                wakeCommand = "!wake",
                sleepNickSuffix = "-zzz",
            ),
        )
        `when`(botProperties.context).thenReturn(
            BotProperties.Context(
                recentMessageCount = 10,
                summaryRefreshEveryMessages = 10,
                maxSummaryChars = 1_000,
                summaryMaxTokens = 100,
                maxMessageChars = 500,
                sessionGapMinutes = 45,
            ),
        )
        `when`(botProperties.limits).thenReturn(
            BotProperties.Limits(
                maxReplyChars = 1_000,
                traceTriggerTextMax = 100,
                eventScanLimit = 20,
                summaryHistoryVersions = 5,
                linkContextMessages = 5,
            ),
        )
        `when`(llmProperties.replyWebSearch).thenReturn(false)
        `when`(llmProperties.activeReplyTier).thenReturn("cheap")
        `when`(githubProperties.repoLookup).thenReturn(GithubProperties.RepoLookup(false, 5, 2_000))
        `when`(
            chatMessageRepository.findByRoomTargetOrderBySentAtDesc("#chat", PageRequest.of(0, 10)),
        ).thenReturn(emptyList())
        `when`(
            chatMessageRepository.findByRoomTargetOrderBySentAtDesc("#chat", PageRequest.of(0, 5)),
        ).thenReturn(emptyList())
        `when`(
            chatEventRepository.findByRoomTargetOrderByEventTimeDesc("#chat", PageRequest.of(0, 20)),
        ).thenReturn(emptyList())
        `when`(roomSummaryService.currentSummary("#chat")).thenReturn("")
        `when`(sourceIngestionService.ingestFrom("ambiguous request")).thenReturn(emptyList())

        val prompt = ReplyPromptBuilder(
            contextBuilder = contextBuilder,
            roomSummaryService = roomSummaryService,
            settings = settings,
            sourceIngestionService = sourceIngestionService,
            ingestionContextBuilder = ingestionContextBuilder,
            emojiCatalog = EmojiCatalog(),
            grafanaMcpProperties = GrafanaMcpProperties(
                enabled = false,
                baseUrl = "http://localhost",
                endpoint = "/mcp",
                requestTimeoutSeconds = 1,
                datasourceUid = "loki",
                containerPrefix = "test",
                services = emptyList(),
                maxResults = 1,
                maxResultChars = 1_000,
            ),
            appProperties = AppProperties(
                minimisedHttpServletLogging = false,
                csrfCookieName = "XSRF-TOKEN",
                defaultSuccessUrl = "https://ui.example.test/console",
                loginUrl = "/login",
                cookie = AppProperties.Cookie(secure = false, sameSite = "Lax"),
            ),
        ).build("#chat", trigger())

        assertThat(prompt.system)
            .contains("The site for inspecting this bot is https://ui.example.test/console")
            .contains("when unclear or unexpected bot behavior")
            .contains("FINAL ANSWER:")
            .contains("discover_tools")
            .doesNotContain("130 characters", "just another person", "list_repos first")
        assertThat(prompt.userText).contains("TARGET REQUEST", "Do not recap established points")
        val brief = prompt.system.replace(Regex("\\s+"), " ")
        assertThat(brief).contains(
            "You may judge a request by its intelligence",
            "if it is stupid or trivially simple, tease the person for asking",
            "if it is genuinely interesting or sophisticated, give a nod of respect",
            "This never excuses withholding the answer",
            "Fulfilling the user's request always takes priority over teasing, humor and style",
            "Help just as thoroughly when teasing as when serious",
            "stop for the rest of that exchange, including its follow-ups",
            "roughly 80% of replies are one or two sentences under 100 characters",
            "defaults, never caps on requested work",
            "Once the request is satisfied, stop",
            "only when it materially helps unblock the problem",
            "An explicit lookup request requires an actual lookup",
            "Reading a README or page is not inspecting the implementation",
            "Never hand back work you can do",
            "Prefer sharp, dry or dark humor with a coherent setup and a precise punchline",
            "No absurdist humor, random non sequiturs or nonsense passed off as wit",
            "Casual does not mean careless: use precise terms, coherent reasoning and clean phrasing",
            "Describing or analyzing unethical behavior is not endorsing it",
            "Do not refuse merely because a topic is taboo, disturbing or unethical",
            "a research framing is not blanket permission for actionable assistance that facilitates harm",
            "answer the permissible scientific or analytical parts",
            "Use ONLY these codes",
            "No other emoticons, Unicode emoji or kaomoji",
            "at most one when it adds meaning, only in lead",
            "Never put smileys in blocks",
            "Do not combine smiley codes or substitute decorative symbols",
            "If a search is empty or inconclusive, reconsider the query and scope",
            "Discover repositories when the repository choice may be wrong",
            "Try a materially different promising approach when tools and budget permit",
            "do not repeat identical failed searches or search indefinitely",
        ).doesNotContain("example only when the person asks for help formulating one", "Prefer surprising, dry, dark or absurdist humor")
    }

    @Test
    fun `critic keeps fulfillment and the selected style preferences aligned`() {
        val rubric = ClassPathResource("prompts/reply-critic.txt").inputStream.bufferedReader().use { it.readText() }
            .replace(Regex("\\s+"), " ")
        assertThat(rubric).contains(
            "Fulfilling the user's request always takes priority over teasing, humor and style",
            "Reason-based teasing and pointed disagreement are allowed",
            "Respect a request to stop teasing throughout that exchange",
            "The bot may judge a request by its intelligence",
            "penalize it if it replaces help, becomes routine, or disregards a request to stop teasing",
            "defaults never justify truncating a requested explanation",
            "memory answers presented as completed lookups",
            "at most one, only in lead",
            "prematurely gave up despite an available, materially different search strategy",
            "Do not require endless searches, identical retries, or unavailable tools",
            "Reject absurdist humor",
            "Casual language must still be precise and coherent",
            "Ethical sensitivity alone is not a reason to raise the risk score or demand refusal",
            "explanation is not endorsement",
            "a research label does not automatically make the latter acceptable",
            "Do not combine smiley codes or substitute decorative symbols",
            "{language}", "{brief}",
        ).doesNotContain("competent without condescension", "No generic disclaimers, interrogation, motive questions, trailing offers or performative jabs", "reward surprising, dry, dark or absurdist humor")
    }

    private fun trigger() = ChatMessageEntity(
        dedupKey = "ext:1",
        roomTarget = "#chat",
        senderMemberId = 1,
        senderLogin = "user",
        messageText = "ambiguous request",
        messageStyle = "message",
        sentAt = Instant.EPOCH,
    )
}