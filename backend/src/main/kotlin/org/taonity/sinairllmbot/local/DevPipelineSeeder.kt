package org.taonity.sinairllmbot.local

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.pipeline.JsonParseFailure
import org.taonity.sinairllmbot.bot.pipeline.LlmCallUsage
import org.taonity.sinairllmbot.bot.pipeline.PipelineAlternative
import org.taonity.sinairllmbot.bot.pipeline.PipelineField
import org.taonity.sinairllmbot.bot.pipeline.PipelineKeys
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import org.taonity.sinairllmbot.bot.pipeline.PipelineStage
import org.taonity.sinairllmbot.bot.pipeline.PipelineStageStatus
import org.taonity.sinairllmbot.bot.pipeline.ToolCallEntry
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@Profile("demo-data")
class DevPipelineSeeder(
    private val pipelineRunRepository: PipelineRunRepository,
    private val objectMapper: ObjectMapper,
    private val settings: BotSettings,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @EventListener(ApplicationReadyEvent::class)
    fun seedOnStartup() {
        val existing = pipelineRunRepository.count()
        if (existing > 0) {
            LOGGER.info { "Skipping demo pipeline seeding: $existing run(s) already present" }
            return
        }
        val room = settings.botRooms().firstOrNull() ?: "#taonity-room"
        val runs = buildFixtures(room)
        pipelineRunRepository.saveAll(runs)
        LOGGER.info { "Seeded ${runs.size} demo pipeline runs into $room (demo-data profile)" }
    }

    private fun buildFixtures(room: String): List<PipelineRunEntity> {
        var minutesAgo = 2L
        fun next() = minutesAgo++

        return listOf(
            reply(
                room, next(), sender = "payload-tester", text = "@segfault payload viewer context test",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-payload-viewer",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "1 candidate · payload viewer fixture",
                    candidates = listOf(candidate("The payload viewer fixture repeats context words for testing.", chosen = true)),
                    extraFields = listOf(PipelineField("fixture", "large-payload")),
                ),
                usage = listOf(payloadViewerTestCall()),
            ),
            reply(
                room, next(), sender = "alice", text = "@segfault какая последняя версия node?",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-1",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "1 candidate", candidates = listOf(candidate("Последняя LTS — Node 22.", chosen = true)),
                    extraFields = listOf(PipelineField("candidates", "1")),
                ),
                usage = listOf(gateCall(respond = true), replyCall()),
            ),
            reply(
                room, next(), sender = "bob", text = "@segfault посоветуй книгу по алгоритмам",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-2",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "2 candidates · chose #1",
                    candidates = listOf(
                        candidate("CLRS, но она тяжёлая.", chosen = false, overall = 6, fit = 6, persona = 5, risk = 2),
                        candidate("Бери «Grokking Algorithms» — заходит легко.", chosen = true, overall = 9, fit = 9, persona = 8, risk = 1),
                    ),
                    extraFields = listOf(PipelineField("candidates", "2"), PipelineField("critic", "used")),
                ),
                usage = listOf(gateCall(respond = true), replyCall(), replyCall(), criticCall()),
            ),
            reply(
                room, next(), sender = "charlie", text = "@segfault ты опять сломался?",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-3",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "2 candidates · chose #0 · repaired",
                    candidates = listOf(
                        candidate("Я в норме, просто задумался :)", chosen = true, overall = 8, fit = 8, persona = 9, risk = 1),
                        candidate("Отвали.", chosen = false, overall = 2, fit = 3, persona = 2, risk = 8),
                    ),
                    extraFields = listOf(
                        PipelineField("candidates", "2"),
                        PipelineField("critic", "used"),
                        PipelineField("repaired", "true"),
                        PipelineField("feedback", "Tone too harsh; softened before sending."),
                    ),
                ),
                usage = listOf(gateCall(respond = true), replyCall(), replyCall(), criticCall(), replyCall(tier = "cheap", tokens = 180)),
            ),
            reply(
                room, next(), sender = "diana", text = "@segfault что там с погодой на выходных?",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-4",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "1 candidate", candidates = listOf(candidate("Обещают дождь в субботу, воскресенье ясно.", chosen = true)),
                    extraFields = listOf(PipelineField("candidates", "1")),
                ),
                usage = listOf(gateCall(respond = true), replyCall(tokens = 620, tools = listOf("web_search"))),
            ),
            reply(
                room, next(), sender = "frank", text = "@segfault где у нас описан ChatCompletionRequest?",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-6",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "1 candidate · repo-grounded",
                    candidates = listOf(candidate("В backend/src/main/kotlin/.../client/ChatCompletionDtos.kt — data class ChatCompletionRequest.", chosen = true)),
                    extraFields = listOf(PipelineField("candidates", "1"), PipelineField("repoLookup", "used")),
                ),
                usage = listOf(gateCall(respond = true), repoReplyCall()),
            ),
            reply(
                room, next(), sender = "eve", text = "@segfault оцени мой код",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-5",
                triage = triageStage(respond = true, category = "addressed"),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "2 candidates · chose #0",
                    candidates = listOf(
                        candidate("Читается норм, но вынеси магические числа в константы.", chosen = true, overall = 8, fit = 8, persona = 7, risk = 2),
                        candidate("Норм.", chosen = false, overall = 4, fit = 4, persona = 4, risk = 3),
                    ),
                    extraFields = listOf(PipelineField("candidates", "2"), PipelineField("critic", "used")),
                ),
                usage = listOf(gateCall(respond = true), replyCall(), replyCall(), criticCall()),
                failures = listOf(
                    JsonParseFailure("triage", 1, "{\"respond\": tru, \"needsFreshInfo\": fa"),
                    JsonParseFailure("critic", 1, "```json\n{\"scores\":[{\"fit\":8,\"persona\": ...truncated"),
                ),
            ),
            reply(
                room, next(), sender = "charlie", text = "лол ну такое",
                outcome = PipelineOutcome.SILENT, outcomeDetail = "driver=none",
                triage = triageStage(respond = false, category = "chatter"),
                decision = decisionStage(reply = false, driver = "none"),
                usage = listOf(gateCall(respond = false)),
            ),
            reply(
                room, next(), sender = "alice", text = "@segfault а ещё?",
                outcome = PipelineOutcome.COOLDOWN,
                stagesOverride = listOf(
                    commandStage(),
                    PipelineStage("cooldown", "Cooldown", PipelineStageStatus.STOP, "on cooldown"),
                ),
            ),
            reply(
                room, next(), sender = "bob", text = "!stop",
                outcome = PipelineOutcome.MUTE_COMMAND, outcomeDetail = "muted by @bob",
                stagesOverride = listOf(PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "mute command")),
            ),
            reply(
                room, next(), sender = "diana", text = "кто-нибудь тут?",
                outcome = PipelineOutcome.MUTED,
                stagesOverride = listOf(
                    commandStage(),
                    PipelineStage("mute", "Mute check", PipelineStageStatus.STOP, "room muted"),
                ),
            ),
            reply(
                room, next(), sender = "bob", text = "!start",
                outcome = PipelineOutcome.UNMUTE_COMMAND, outcomeDetail = "un-muted by @bob",
                stagesOverride = listOf(PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "un-mute command")),
            ),
            summary(
                room, next(), outcome = PipelineOutcome.SUMMARY_REFRESHED,
                stage = PipelineStage(
                    "summary", "Summary refresh", PipelineStageStatus.OK, "40 messages · 1180 chars",
                    fields = listOf(
                        PipelineField("tier", "gate"),
                        PipelineField("messages", "40"),
                        PipelineField("newMessages", "40"),
                        PipelineField("newChars", "1180"),
                        PipelineField("source", "job: scheduled refresh"),
                    ),
                ),
                usage = listOf(LlmCallUsage("gate", "stub/gate", 900, requestPayload = requestJson("summarise"), responsePayload = responseJson("Ребята обсуждали Node и погоду."))),
            ),
            summary(
                room, next(), outcome = PipelineOutcome.SUMMARY_FAILED, outcomeDetail = "empty summary",
                stage = PipelineStage(
                    "summary", "Summary refresh", PipelineStageStatus.STOP, "model returned no summary",
                    fields = listOf(PipelineField("tier", "gate"), PipelineField("source", "job: scheduled refresh")),
                ),
                usage = listOf(LlmCallUsage("gate", "stub/gate", 120, requestPayload = requestJson("summarise"), responsePayload = responseJson(""))),
            ),
        )
    }


    private fun commandStage() = PipelineStage("command", "Command gate", PipelineStageStatus.PASS, "no command")

    private fun triageStage(
        respond: Boolean,
        category: String,
    ) = PipelineStage(
        key = "triage", label = "Triage", status = PipelineStageStatus.OK,
        summary = "respond=$respond · $category",
        fields = listOf(
            PipelineField("respond", respond.toString()),
            PipelineField("category", category),
        ),
    )

    private fun decisionStage(reply: Boolean, driver: String) = PipelineStage(
        key = "decision", label = "Reply decision",
        status = if (reply) PipelineStageStatus.OK else PipelineStageStatus.STOP,
        summary = if (reply) "reply (driver=$driver)" else "stay silent",
        fields = listOf(PipelineField("driver", driver), PipelineField("reply", reply.toString())),
    )

    private fun generateStage(
        summary: String,
        candidates: List<PipelineAlternative>,
        extraFields: List<PipelineField>,
    ) = PipelineStage(
        key = "generate", label = "Reply generation", status = PipelineStageStatus.OK,
        summary = summary, fields = extraFields, alternatives = candidates,
    )

    private fun candidate(
        text: String,
        chosen: Boolean,
        overall: Int? = null,
        fit: Int? = null,
        persona: Int? = null,
        risk: Int? = null,
    ) = PipelineAlternative(
        text = text, chosen = chosen,
        fields = buildList {
            overall?.let { add(PipelineField("overall", it.toString())) }
            fit?.let { add(PipelineField("fit", it.toString())) }
            persona?.let { add(PipelineField("persona", it.toString())) }
            risk?.let { add(PipelineField("risk", it.toString())) }
        },
    )


    private fun gateCall(respond: Boolean) = LlmCallUsage(
        tier = "gate", model = "stub/gate", tokens = 18, promptTokens = 14, completionTokens = 4,
        requestPayload = requestJson("triage"),
        responsePayload = responseJson("{\"respond\": $respond}"),
    )

    private fun replyCall(tier: String = "cheap", tokens: Int = 320, tools: List<String> = emptyList()) = LlmCallUsage(
        tier = tier, model = "stub/cheap", tokens = tokens,
        promptTokens = (tokens * 0.75).toInt(), completionTokens = tokens - (tokens * 0.75).toInt(),
        tools = tools,
        requestPayload = requestJson("reply"), responsePayload = responseJson("…"),
    )

    private fun repoReplyCall(tokens: Int = 880) = LlmCallUsage(
        tier = "repo", model = "anthropic/claude-3.5-sonnet", tokens = tokens,
        promptTokens = 720, completionTokens = 160,
        tools = listOf("search_code", "get_file"),
        toolCalls = listOf(
            ToolCallEntry(
                name = "search_code",
                arguments = """{"query":"ChatCompletionRequest","repo":"sinair-llm-bot"}""",
                result = "sinair-llm-bot/backend/src/main/kotlin/org/taonity/sinairllmbot/bot/client/ChatCompletionDtos.kt\n" +
                    "sinair-llm-bot/backend/src/main/kotlin/org/taonity/sinairllmbot/bot/client/LlmClient.kt",
            ),
            ToolCallEntry(
                name = "get_file",
                arguments = """{"repo":"sinair-llm-bot","path":"backend/src/main/kotlin/org/taonity/sinairllmbot/bot/client/ChatCompletionDtos.kt"}""",
                result = "sinair-llm-bot/backend/src/main/kotlin/org/taonity/sinairllmbot/bot/client/ChatCompletionDtos.kt:\n" +
                    "data class ChatCompletionRequest(\n" +
                    "    val model: String,\n" +
                    "    val messages: List<ChatMessage>,\n" +
                    "    val temperature: Double? = null,\n" +
                    "    @JsonProperty(\"max_tokens\") val maxTokens: Int? = null,\n" +
                    "    val tools: List<Tool>? = null,\n" +
                    ")\n... [truncated]",
            ),
        ),
        requestPayload = requestJson("reply with repo tools"),
        responsePayload = responseJson("…"),
    )

    private fun criticCall() = LlmCallUsage(
        tier = "critic", model = "stub/critic", tokens = 90, promptTokens = 70, completionTokens = 20,
        requestPayload = requestJson("critic"), responsePayload = responseJson("{\"scores\":[]}"),
    )

    private fun payloadViewerTestCall() = LlmCallUsage(
        tier = "payload-test", model = "stub/payload-test", tokens = 1_240, promptTokens = 920, completionTokens = 320,
        requestPayload = """
            {
              "model": "stub/payload-test",
              "metadata": {
                "fixture": "payload-viewer-context-fixture",
                "context": "context context context",
                "contextual": "contextual contextually recontextualized",
                "subcontext": "subcontext context subcontext",
                "labels": ["context", "contextual", "subcontext", "context"]
              },
              "messages": [
                {
                  "role": "system",
                  "content": "Use the supplied context. Keep context, contextual detail, and subcontext distinct."
                },
                {
                  "role": "user",
                  "content": "Summarize the context. Repeat context only when the contextual subcontext requires context.",
                  "contextBlocks": [
                    {
                      "id": "context-001",
                      "title": "Primary context",
                      "text": "Context establishes the shared context for every contextual decision.",
                      "tags": ["context", "shared-context", "contextual"]
                    },
                    {
                      "id": "context-002",
                      "title": "Nested subcontext",
                      "text": "This subcontext repeats context context context and adds contextual metadata.",
                      "tags": ["subcontext", "context", "recontextualized"]
                    },
                    {
                      "id": "context-003",
                      "title": "Context comparison",
                      "text": "Compare context with contextual and noncontextual values in this context fixture.",
                      "tags": ["context", "contextual", "noncontextual", "context"]
                    }
                  ]
                }
              ],
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "store_context",
                    "description": "Stores context and subcontext for contextual follow-up.",
                    "parameters": {
                      "type": "object",
                      "properties": {
                        "context": { "type": "string" },
                        "subcontext": { "type": "string" },
                        "contextual": { "type": "boolean" }
                      },
                      "required": ["context", "subcontext"]
                    }
                  }
                }
              ]
            }
        """.trimIndent(),
        responsePayload = """
            {
              "id": "payload-viewer-response-001",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "The context is repeated in each subcontext so contextual comparisons can be tested.",
                    "tool_calls": [
                      {
                        "id": "call_context_001",
                        "type": "function",
                        "function": {
                          "name": "store_context",
                          "arguments": "{\\"context\\":\\"context context context\\",\\"subcontext\\":\\"nested contextual subcontext\\",\\"contextual\\":true}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ],
              "usage": {
                "prompt_tokens": 920,
                "completion_tokens": 320,
                "total_tokens": 1240
              }
            }
        """.trimIndent(),
    )

    private fun requestJson(kind: String) = "{\"model\":\"stub\",\"messages\":[{\"role\":\"user\",\"content\":\"$kind prompt\"}]}"

    private fun responseJson(content: String) =
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"$content\"}}]}"


    private fun reply(
        room: String,
        minutesAgo: Long,
        sender: String,
        text: String,
        outcome: String,
        outcomeDetail: String? = null,
        outboundId: String? = null,
        triage: PipelineStage? = null,
        decision: PipelineStage? = null,
        generate: PipelineStage? = null,
        stagesOverride: List<PipelineStage>? = null,
        usage: List<LlmCallUsage> = emptyList(),
        failures: List<JsonParseFailure> = emptyList(),
    ): PipelineRunEntity {
        val stages = stagesOverride ?: buildList {
            add(commandStage())
            add(PipelineStage("cooldown", "Cooldown", PipelineStageStatus.PASS, "ready"))
            triage?.let { add(it) }
            decision?.let { add(it) }
            generate?.let { add(it) }
        }
        return entity(
            pipelineKey = PipelineKeys.REPLY, room = room, minutesAgo = minutesAgo,
            sender = sender, text = text, triggerMessageId = "demo-msg-$minutesAgo",
            outcome = outcome, outcomeDetail = outcomeDetail, outboundId = outboundId,
            stages = stages, usage = usage, failures = failures,
        )
    }

    private fun summary(
        room: String,
        minutesAgo: Long,
        outcome: String,
        outcomeDetail: String? = null,
        stage: PipelineStage,
        usage: List<LlmCallUsage>,
    ) = entity(
        pipelineKey = PipelineKeys.SUMMARY, room = room, minutesAgo = minutesAgo,
        sender = "system", text = "Summary refresh · job: scheduled refresh", triggerMessageId = null,
        outcome = outcome, outcomeDetail = outcomeDetail, outboundId = null,
        stages = listOf(stage), usage = usage, failures = emptyList(),
    )

    private fun entity(
        pipelineKey: String,
        room: String,
        minutesAgo: Long,
        sender: String,
        text: String,
        triggerMessageId: String?,
        outcome: String,
        outcomeDetail: String?,
        outboundId: String?,
        stages: List<PipelineStage>,
        usage: List<LlmCallUsage>,
        failures: List<JsonParseFailure>,
    ) = PipelineRunEntity(
        pipelineKey = pipelineKey,
        roomTarget = room,
        triggerMessageId = triggerMessageId,
        triggerSenderLogin = sender,
        triggerText = text,
        outcome = outcome,
        outcomeDetail = outcomeDetail,
        outboundMessageId = outboundId,
        stagesJson = objectMapper.writeValueAsString(stages),
        totalTokens = usage.sumOf { it.tokens },
        llmUsageJson = objectMapper.writeValueAsString(usage),
        jsonParseFailureCount = failures.size,
        jsonParseFailuresJson = objectMapper.writeValueAsString(failures),
        createdAt = Instant.now().minus(minutesAgo, ChronoUnit.MINUTES),
    )
}
