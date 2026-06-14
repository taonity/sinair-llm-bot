package org.taonity.sinairllmbot.chat.controller

import org.taonity.sinairllmbot.chat.dto.IngestRequest
import org.taonity.sinairllmbot.chat.dto.IngestResponse
import org.taonity.sinairllmbot.chat.service.ChatIngestService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/chat")
class ChatIngestController(
    private val chatIngestService: ChatIngestService
) {

    @PostMapping("/ingest")
    fun ingest(@RequestBody request: IngestRequest): IngestResponse {
        return chatIngestService.ingest(request)
    }
}
