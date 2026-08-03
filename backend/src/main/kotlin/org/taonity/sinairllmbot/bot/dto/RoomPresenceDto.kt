package org.taonity.sinairllmbot.bot.dto

data class RoomPresenceDto(
    val roomTarget: String,
    val presence: BotPresence,
    val nickSuffix: String = "",
)

enum class BotPresence {
    BACK,

    AWAY,
}
