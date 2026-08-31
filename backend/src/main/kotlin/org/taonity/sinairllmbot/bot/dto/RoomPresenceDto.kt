package org.taonity.sinairllmbot.bot.dto

data class RoomPresenceDto(
    val roomTarget: String,
    val presence: BotPresence,
    val nickname: String,
    val nickSuffix: String = "",
)

enum class BotPresence {
    BACK,

    AWAY,
}
