package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
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
                prompt = Prompt("Test persona."),
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
            .contains("materially ambiguous or underspecified")
            .contains("show a compact example of how their request could ideally be written")
            .contains("Ask at most one focused clarifying question")
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