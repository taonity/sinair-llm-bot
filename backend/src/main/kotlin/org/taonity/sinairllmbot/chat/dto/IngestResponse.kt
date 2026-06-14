package org.taonity.sinairllmbot.chat.dto

data class IngestResponse(
    val messagesStored: Int,
    val messagesDuplicate: Int,
    val eventsStored: Int,
    val eventsDuplicate: Int
)
