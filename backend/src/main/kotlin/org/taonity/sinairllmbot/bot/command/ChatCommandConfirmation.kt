package org.taonity.sinairllmbot.bot.command

import org.taonity.sinairllmbot.chat.entity.ChatEventEntity

internal object ChatCommandConfirmation {
    fun matches(event: ChatEventEntity, memberId: Int?, command: String, arguments: String): Boolean {
        if (memberId == null || event.memberId != memberId) return false
        return when (command) {
            "nick" -> event.status == "nick_change" && event.memberName == arguments.trim()
            "color" -> event.status == "color_change" && event.memberColor.equals(arguments.trim(), ignoreCase = true)
            "gender" -> event.status == "gender_change" && event.isGirl == (arguments.trim() == "f")
            else -> false
        }
    }
}