package org.taonity.sinairllmbot.bot.metrics

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.config.BotProperties
import org.taonity.sinairllmbot.bot.pipeline.PipelineOutcome
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.bot.service.BotSleepService
import org.taonity.sinairllmbot.bot.service.MutedRoomRegistry
import org.taonity.sinairllmbot.config.BotSettings
import java.time.Instant

class BotMetricsTest {
    @Test
    fun `participation counters export zero before traffic and stable platform labels`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        try {
            val metrics = BotMetrics(registry)
            assertThat(registry.scrape())
                .contains("bot_chat_human_messages_total{platform=\"sinair\"} 0.0")
                .contains("bot_replies_acknowledged_total{platform=\"sinair\"} 0.0")

            metrics.recordHumanMessages(10)
            metrics.recordHumanMessages(2)
            metrics.recordAcknowledgedReplies(3)
            metrics.recordPipelineRun(PipelineOutcome.COOLDOWN)
            metrics.recordPipelineRun(PipelineOutcome.FAILED)

            assertThat(registry.scrape())
                .contains("bot_chat_human_messages_total{platform=\"sinair\"} 12.0")
                .contains("bot_replies_acknowledged_total{platform=\"sinair\"} 3.0")
                .contains("bot_pipeline_runs_total{outcome=\"cooldown\",platform=\"sinair\"} 1.0")
                .contains("bot_pipeline_runs_total{outcome=\"failed\",platform=\"sinair\"} 1.0")
        } finally {
            registry.close()
        }
    }

    @Test
    fun `state snapshots exclude paused inboxes and keep stale data on database failure`() {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        try {
            val settings = mock(BotSettings::class.java)
            val properties = mock(BotProperties::class.java)
            val pending = mock(PendingBotMessageRepository::class.java)
            val outbound = mock(OutboundMessageRepository::class.java)
            val muted = mock(MutedRoomRegistry::class.java)
            val sleeping = mock(BotSleepService::class.java)
            `when`(settings.bot()).thenReturn(properties)
            `when`(properties.enabled).thenReturn(true)
            `when`(settings.botRooms()).thenReturn(listOf("#active", "#muted", "#sleeping"))
            `when`(muted.isMuted("#muted")).thenReturn(true)
            `when`(sleeping.isAsleep("#sleeping")).thenReturn(true)
            `when`(pending.countByRoomTargetIn(listOf("#active"))).thenReturn(2)
            `when`(pending.oldestCreatedAt(listOf("#active"))).thenReturn(Instant.ofEpochSecond(100))
            val metrics = BotStatusMetrics(registry, settings, pending, outbound, muted, sleeping)
            assertThat(registry.get("bot.metrics.snapshot.timestamp.seconds").gauge().value()).isZero()
            metrics.refresh()
            assertThat(registry.get("bot.state.active.rooms").gauge().value()).isEqualTo(1.0)
            assertThat(registry.get("bot.queue.messages").tag("queue", "inbox").gauge().value()).isEqualTo(2.0)
            val timestamp = registry.get("bot.metrics.snapshot.timestamp.seconds").gauge().value()
            `when`(pending.countByRoomTargetIn(listOf("#active"))).thenThrow(IllegalStateException("offline"))
            metrics.refresh()
            assertThat(registry.get("bot.metrics.snapshot.timestamp.seconds").gauge().value()).isEqualTo(timestamp)
            metrics.collectorHeartbeat(setOf("#active", "#unconfigured"), false)
            assertThat(registry.get("bot.collector.joined.rooms").gauge().value()).isEqualTo(1.0)
            assertThat(registry.get("bot.collector.sending.enabled").gauge().value()).isZero()
            assertThat(registry.get("bot.collector.heartbeat.timestamp.seconds").gauge().value()).isPositive()
            clearInvocations(pending)
            `when`(properties.enabled).thenReturn(false)
            metrics.refresh()
            assertThat(registry.get("bot.state.enabled").gauge().value()).isZero()
            verifyNoInteractions(pending)
        } finally {
            registry.close()
        }
    }
}