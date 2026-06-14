package org.taonity.sinairllmbot.chat.dto

data class IngestRequest(
    val messages: List<ChatMessageDto> = emptyList(),
    val events: List<ChatEventDto> = emptyList()
)
