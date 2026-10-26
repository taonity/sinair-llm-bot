package org.taonity.sinairllmbot.bot.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.bot.service.BotSleepService
import org.taonity.sinairllmbot.bot.service.MutedRoomRegistry
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class BotStatusMetrics(
    private val registry: MeterRegistry,
    private val settings: BotSettings,
    private val pendingRepository: PendingBotMessageRepository,
    private val outboundRepository: OutboundMessageRepository,
    private val mutedRooms: MutedRoomRegistry,
    private val sleepService: BotSleepService,
    private val messages: ChatMessageRepository,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val WAITING = listOf(OutboundStatus.PENDING, OutboundStatus.CLAIMED)
        private const val CHAT_WINDOW_SIZE = 100
    }

    @Volatile private var snapshot = Snapshot()
    @Volatile private var collector = CollectorSnapshot()
    private val registeredRooms = ConcurrentHashMap.newKeySet<String>()

    init {
        gauge(registry, "bot.state.enabled") { snapshot.enabled }
        gauge(registry, "bot.state.configured.rooms") { snapshot.configuredRooms }
        gauge(registry, "bot.state.active.rooms") { snapshot.activeRooms }
        gauge(registry, "bot.state.muted.rooms") { snapshot.mutedRooms }
        gauge(registry, "bot.state.sleeping.rooms") { snapshot.sleepingRooms }
        gauge(registry, "bot.metrics.snapshot.timestamp.seconds") { snapshot.timestamp }
        gauge(registry, "bot.collector.heartbeat.timestamp.seconds") { collector.timestamp }
        gauge(registry, "bot.collector.joined.rooms") { collector.rooms.intersect(settings.botRooms().toSet()).size.toDouble() }
        gauge(registry, "bot.collector.sending.enabled") { collector.sendingEnabled }
        for (queue in listOf("inbox", "outbound")) {
            Gauge.builder("bot.queue.messages", this) {
                if (queue == "inbox") it.snapshot.inboxCount else it.snapshot.outboundCount
            }.tags("platform", "sinair", "queue", queue).register(registry)
            Gauge.builder("bot.queue.oldest.timestamp.seconds", this) {
                if (queue == "inbox") it.snapshot.inboxOldest else it.snapshot.outboundOldest
            }.tags("platform", "sinair", "queue", queue).register(registry)
        }
    }

    fun collectorHeartbeat(rooms: Set<String>, sendingEnabled: Boolean) {
        collector = CollectorSnapshot(Instant.now().epochSecond.toDouble(), rooms.toSet(), if (sendingEnabled) 1.0 else 0.0)
    }

    @Scheduled(fixedDelayString = "\${app.observability.snapshot-interval-ms}")
    fun refresh() {
        runCatching {
            val rooms = settings.botRooms().toSet()
            val enabled = settings.bot().enabled
            val awakeRooms = rooms.filterNot { sleepService.isAsleep(it) }
            val activeRooms = awakeRooms.filterNot { mutedRooms.isMuted(it) }
            val personaName = settings.bot().persona.name
            val windows = rooms.associateWith { room ->
                val recent = messages.findByRoomTargetOrderBySentAtDescIdDesc(room, PageRequest.of(0, CHAT_WINDOW_SIZE))
                ChatWindow(recent.size.toDouble(), recent.count {
                    it.sourceOutboundMessageId != null || it.senderLogin.equals(personaName, ignoreCase = true)
                }.toDouble())
            }
            for (room in rooms.filter { registeredRooms.add(it) }) {
                Gauge.builder("bot.chat.window.messages", this) { it.snapshot.windows[room]?.total ?: Double.NaN }
                    .tags("platform", "sinair", "room", room, "window", CHAT_WINDOW_SIZE.toString()).register(registry)
                Gauge.builder("bot.chat.window.bot.messages", this) { it.snapshot.windows[room]?.bot ?: Double.NaN }
                    .tags("platform", "sinair", "room", room, "window", CHAT_WINDOW_SIZE.toString()).register(registry)
            }
            snapshot = Snapshot(
                enabled = if (enabled) 1.0 else 0.0,
                configuredRooms = rooms.size.toDouble(),
                activeRooms = if (enabled) activeRooms.size.toDouble() else 0.0,
                mutedRooms = rooms.count { mutedRooms.isMuted(it) }.toDouble(),
                sleepingRooms = (rooms.size - awakeRooms.size).toDouble(),
                inboxCount = if (activeRooms.isEmpty() || !enabled) 0.0 else pendingRepository.countByRoomTargetIn(activeRooms).toDouble(),
                inboxOldest = if (activeRooms.isEmpty() || !enabled) 0.0 else pendingRepository.oldestCreatedAt(activeRooms)?.epochSecond?.toDouble() ?: 0.0,
                outboundCount = if (rooms.isEmpty()) 0.0 else outboundRepository.countByRoomTargetInAndStatusIn(rooms, WAITING).toDouble(),
                outboundOldest = if (rooms.isEmpty()) 0.0 else outboundRepository.oldestCreatedAt(rooms, WAITING)?.epochSecond?.toDouble() ?: 0.0,
                timestamp = Instant.now().epochSecond.toDouble(),
                windows = windows,
            )
        }.onFailure { LOGGER.warn { "Bot metrics snapshot failed: ${it.javaClass.simpleName}" } }
    }

    private fun gauge(registry: MeterRegistry, name: String, value: () -> Double) {
        Gauge.builder(name, value) { it() }.tag("platform", "sinair").strongReference(true).register(registry)
    }

    private data class Snapshot(
        val enabled: Double = Double.NaN,
        val configuredRooms: Double = Double.NaN,
        val activeRooms: Double = Double.NaN,
        val mutedRooms: Double = Double.NaN,
        val sleepingRooms: Double = Double.NaN,
        val inboxCount: Double = Double.NaN,
        val inboxOldest: Double = Double.NaN,
        val outboundCount: Double = Double.NaN,
        val outboundOldest: Double = Double.NaN,
        val timestamp: Double = 0.0,
        val windows: Map<String, ChatWindow> = emptyMap(),
    )

    private data class ChatWindow(val total: Double, val bot: Double)

    private data class CollectorSnapshot(
        val timestamp: Double = 0.0,
        val rooms: Set<String> = emptySet(),
        val sendingEnabled: Double = Double.NaN,
    )
}
