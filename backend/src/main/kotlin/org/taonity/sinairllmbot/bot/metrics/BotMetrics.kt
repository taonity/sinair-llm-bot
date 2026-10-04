package org.taonity.sinairllmbot.bot.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import java.time.Duration

@Component
class BotMetrics(registry: MeterRegistry) {
    private val humanMessages = Counter.builder("bot.chat.human.messages")
        .description("New live human messages stored in configured bot rooms; excludes history and bot echoes")
        .tag("platform", "sinair")
        .register(registry)
    private val acknowledgedReplies = Counter.builder("bot.replies.acknowledged")
        .description("Outbound replies acknowledged by the collector; not chat-server delivery receipts")
        .tag("platform", "sinair")
        .register(registry)
    private val replyLatency = Timer.builder("bot.reply.ack.latency")
        .description("Time from trigger ingestion to collector acknowledgment, including intentional waiting")
        .tag("platform", "sinair")
        .serviceLevelObjectives(*listOf(5L, 15L, 30L, 60L, 120L, 300L, 900L).map(Duration::ofSeconds).toTypedArray())
        .register(registry)
    private val pipelineRuns = listOf(
        PipelineOutcome.REPLIED, PipelineOutcome.FAILED, PipelineOutcome.SILENT,
        PipelineOutcome.MUTED, PipelineOutcome.COOLDOWN, PipelineOutcome.MUTE_COMMAND,
        PipelineOutcome.UNMUTE_COMMAND, PipelineOutcome.SUMMARY_REFRESHED, PipelineOutcome.SUMMARY_FAILED,
    ).associateWith { outcome ->
        Counter.builder("bot.pipeline.runs")
            .description("Pipeline evaluation attempts, including reevaluations of deferred messages")
            .tags("platform", "sinair", "outcome", outcome.lowercase())
            .register(registry)
    }

    fun recordHumanMessages(count: Int) {
        humanMessages.increment(count.toDouble())
    }

    fun recordAcknowledgedReplies(count: Int) {
        acknowledgedReplies.increment(count.toDouble())
    }

    fun recordPipelineRun(outcome: String) {
        pipelineRuns[outcome]?.increment()
    }

    fun recordReplyLatency(duration: Duration) {
        if (!duration.isNegative) replyLatency.record(duration)
    }
}