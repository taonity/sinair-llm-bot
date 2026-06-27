package org.taonity.sinairllmbot.bot.dto

data class OutboundMessageDto(
    val id: String,
    val roomTarget: String,
    val messageText: String,
    val replyToExternalId: String?,
)

data class OutboundAckRequest(
    val ids: List<String> = emptyList(),
)

data class OutboundAckResponse(
    val acknowledged: Int,
)
