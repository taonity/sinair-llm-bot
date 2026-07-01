package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks rooms where the bot has been muted via the `!stop` command (and un-muted via `!start`).
 *
 * Shared between the message pipeline (which honours the mute when deciding to reply) and the
 * presence service (a muted room reports the bot as offline).
 */
@Component
class MutedRoomRegistry {
    private val mutedRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun mute(roomTarget: String): Boolean = mutedRooms.add(roomTarget)

    /** Returns true if the room was previously muted. */
    fun unmute(roomTarget: String): Boolean = mutedRooms.remove(roomTarget)

    fun isMuted(roomTarget: String): Boolean = roomTarget in mutedRooms
}
