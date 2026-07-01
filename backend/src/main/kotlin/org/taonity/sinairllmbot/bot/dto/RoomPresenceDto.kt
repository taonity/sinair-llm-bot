package org.taonity.sinairllmbot.bot.dto

/**
 * The bot's availability in a room, polled by the chat collector so it can reflect the bot's
 * presence in the chat via the away/back status toggle.
 */
data class RoomPresenceDto(
    val roomTarget: String,
    val presence: BotPresence,
)

enum class BotPresence {
    /** The bot is enabled, un-muted and off cooldown — ready to reply. */
    BACK,

    /** The bot is muted (`!stop`) or on cooldown/rate-limited — not currently replying. */
    AWAY,
}
