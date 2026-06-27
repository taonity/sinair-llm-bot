package org.taonity.sinairllmbot.chat.dto

data class ChatMessageDto(
    val externalId: String?,
    val roomTarget: String,
    val senderMemberId: Int,
    val senderUserId: Int = 0,
    val senderLogin: String,
    val senderColor: String?,
    val messageText: String,
    val messageStyle: String,
    val recipientMemberId: Int = 0,
    val sentAt: Long
)
