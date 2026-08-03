package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.bot.dto.BotPresence
import org.taonity.sinairllmbot.bot.dto.RoomPresenceDto

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
        settings.botRooms().map { room ->
            val nickSuffix = if (botSleepService.isAsleep(room)) botProperties.persona.sleepNickSuffix else ""
            RoomPresenceDto(room, presenceFor(room), nickSuffix)
        }
}
