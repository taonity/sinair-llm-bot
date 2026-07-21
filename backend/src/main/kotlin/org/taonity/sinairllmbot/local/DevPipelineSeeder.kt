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
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Dev-only: seeds one representative [PipelineRunEntity] per outcome/kind on startup so the console
 * "Pipelines" tab (and its detail view: stages, alternatives, LLM usage, JSON-parse failures) can be
 * exercised without waiting for real bot traffic — which, under the stubs, only ever produces SILENT
 * runs. Gated on the opt-in `demo-data` profile and only seeds when the table is empty, so it never
 * duplicates rows or touches real data. Independent of the OAuth2 stub (works under any profile set).
 */
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
        val room = settings.bot().rooms.firstOrNull() ?: "#test1"
        val runs = buildFixtures(room)
        pipelineRunRepository.saveAll(runs)
        LOGGER.info { "Seeded ${runs.size} demo pipeline runs into $room (demo-data profile)" }
    }

    /**
     * One demo run per kind, newest first. [minutesAgo] staggers [PipelineRunEntity.createdAt] so the
     * console's newest-first ordering shows them in a natural sequence.
     */
    private fun buildFixtures(room: String): List<PipelineRunEntity> {
        var minutesAgo = 2L
        fun next() = minutesAgo++

        return listOf(
            // A plain reply: triage said respond, one candidate, critic disabled.
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
            // A reply chosen by the critic between two candidates.
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
            // A reply the critic repaired before sending.
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
            // A reply that needed fresh info, so the reply call used the web_search tool.
            reply(
                room, next(), sender = "diana", text = "@segfault что там с погодой на выходных?",
                outcome = PipelineOutcome.REPLIED, outboundId = "demo-out-4",
                triage = triageStage(respond = true, category = "addressed", needsFreshInfo = true, needsSearch = true),
                decision = decisionStage(reply = true, driver = "triage"),
                generate = generateStage(
                    summary = "1 candidate", candidates = listOf(candidate("Обещают дождь в субботу, воскресенье ясно.", chosen = true)),
                    extraFields = listOf(PipelineField("candidates", "1")),
                ),
                usage = listOf(gateCall(respond = true), replyCall(tokens = 620, tools = listOf("web_search"))),
            ),
            // A reply where the model's JSON was malformed and the prompt was retried (new resilience feature).
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
            // Triage decided to stay silent (the common case under the stubs).
            reply(
                room, next(), sender = "charlie", text = "лол ну такое",
                outcome = PipelineOutcome.SILENT, outcomeDetail = "driver=none",
                triage = triageStage(respond = false, category = "chatter"),
                decision = decisionStage(reply = false, driver = "none"),
                usage = listOf(gateCall(respond = false)),
            ),
            // On cooldown after a recent reply.
            reply(
                room, next(), sender = "alice", text = "@segfault а ещё?",
                outcome = PipelineOutcome.COOLDOWN,
                stagesOverride = listOf(
                    commandStage(),
                    PipelineStage("cooldown", "Cooldown", PipelineStageStatus.STOP, "on cooldown"),
                ),
            ),
            // A "stop the bot" command.
            reply(
                room, next(), sender = "bob", text = "!stop",
                outcome = PipelineOutcome.MUTE_COMMAND, outcomeDetail = "muted by @bob",
                stagesOverride = listOf(PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "mute command")),
            ),
            // A message arriving while the room is muted.
            reply(
                room, next(), sender = "diana", text = "кто-нибудь тут?",
                outcome = PipelineOutcome.MUTED,
                stagesOverride = listOf(
                    commandStage(),
                    PipelineStage("mute", "Mute check", PipelineStageStatus.STOP, "room muted"),
                ),
            ),
            // A "start the bot" command.
            reply(
                room, next(), sender = "bob", text = "!start",
                outcome = PipelineOutcome.UNMUTE_COMMAND, outcomeDetail = "un-muted by @bob",
                stagesOverride = listOf(PipelineStage("command", "Command gate", PipelineStageStatus.STOP, "un-mute command")),
            ),
            // A rolling-summary refresh (its own pipeline kind), driven by a background job.
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
            // A summary refresh that failed (model returned nothing).
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

    // --- stage builders (mirror BotMessageOrchestrator / RoomSummaryService output) ---

    private fun commandStage() = PipelineStage("command", "Command gate", PipelineStageStatus.PASS, "no command")

    private fun triageStage(
        respond: Boolean,
        category: String,
        needsFreshInfo: Boolean = false,
        needsSearch: Boolean = false,
    ) = PipelineStage(
        key = "triage", label = "Triage", status = PipelineStageStatus.OK,
        summary = "respond=$respond · $category",
        fields = listOf(
            PipelineField("respond", respond.toString()),
            PipelineField("category", category),
            PipelineField("needsFreshInfo", needsFreshInfo.toString()),
            PipelineField("needsSearch", needsSearch.toString()),
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

    // --- LLM call builders ---

    private fun gateCall(respond: Boolean) = LlmCallUsage(
        tier = "gate", model = "stub/gate", tokens = 18,
        requestPayload = requestJson("triage"),
        responsePayload = responseJson("{\"respond\": $respond}"),
    )

    private fun replyCall(tier: String = "cheap", tokens: Int = 320, tools: List<String> = emptyList()) = LlmCallUsage(
        tier = tier, model = "stub/cheap", tokens = tokens, tools = tools,
        requestPayload = requestJson("reply"), responsePayload = responseJson("…"),
    )

    private fun criticCall() = LlmCallUsage(
        tier = "critic", model = "stub/critic", tokens = 90,
        requestPayload = requestJson("critic"), responsePayload = responseJson("{\"scores\":[]}"),
    )

    private fun requestJson(kind: String) = "{\"model\":\"stub\",\"messages\":[{\"role\":\"user\",\"content\":\"$kind prompt\"}]}"

    private fun responseJson(content: String) =
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"$content\"}}]}"

    // --- entity assembly ---

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
