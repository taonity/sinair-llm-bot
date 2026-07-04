package org.taonity.sinairllmbot.bot.dto

/**
 * The bot's availability in a room, polled by the collector. [nickSuffix] is appended to the
 * collector's bot nick to visibly mark an inactive (asleep) bot; empty while active.
 */
data class RoomPresenceDto(
    val roomTarget: String,
    val presence: BotPresence,
    val nickSuffix: String = "",
)

enum class BotPresence {
    /** The bot is enabled, un-muted, awake and off cooldown — ready to reply. */
    BACK,

    /** The bot is asleep (`!sleep`), muted (`!stop`) or on cooldown/rate-limited — not replying. */
    AWAY,
}
