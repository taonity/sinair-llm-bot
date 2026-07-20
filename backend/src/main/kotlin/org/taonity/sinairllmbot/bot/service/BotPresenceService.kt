package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.dto.BotPresence
import org.taonity.sinairllmbot.bot.dto.RoomPresenceDto

/**
 * Computes the bot's live presence (away/back) per room from its readiness to reply.
 *
 * A room is [BotPresence.BACK] only when the bot is enabled, awake (not `!sleep`), not muted via
 * `!stop`, and off cooldown (the rate limiter would allow a reply right now); otherwise it is
 * [BotPresence.AWAY]. While asleep the reported nick also carries [BotProperties.Persona.sleepNickSuffix].
 * Presence is derived on demand, so a cooldown that has elapsed flips the room back at the next
 * poll without any scheduling. The reply debouncer deliberately has no effect here.
 */
@Service
class BotPresenceService(
    private val settings: BotSettings,
    private val cooldownTracker: BotCooldownTracker,
    private val mutedRoomRegistry: MutedRoomRegistry,
    private val botSleepService: BotSleepService,
) {
    private val botProperties get() = settings.bot()

    fun presenceFor(roomTarget: String): BotPresence {
        if (botSleepService.isAsleep(roomTarget)) return BotPresence.AWAY
        val ready = botProperties.enabled &&
            !mutedRoomRegistry.isMuted(roomTarget) &&
            cooldownTracker.canReply(roomTarget)
        return if (ready) BotPresence.BACK else BotPresence.AWAY
    }

    fun allPresences(): List<RoomPresenceDto> =
        botProperties.rooms.map { room ->
            val nickSuffix = if (botSleepService.isAsleep(room)) botProperties.persona.sleepNickSuffix else ""
            RoomPresenceDto(room, presenceFor(room), nickSuffix)
        }
}
