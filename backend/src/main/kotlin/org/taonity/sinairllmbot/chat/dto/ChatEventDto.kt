package org.taonity.sinairllmbot.chat.dto

data class ChatEventDto(
    val roomTarget: String,
    val memberId: Int,
    val userId: Int = 0,
    val memberName: String,
    val memberColor: String?,
    val status: String,
    val eventData: String?,
    val isGirl: Boolean = false,
    val isModer: Boolean = false,
    val isOwner: Boolean = false,
    val eventTime: Long
)
