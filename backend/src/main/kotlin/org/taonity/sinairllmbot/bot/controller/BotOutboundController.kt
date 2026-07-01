package org.taonity.sinairllmbot.bot.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.bot.dto.OutboundAckRequest
import org.taonity.sinairllmbot.bot.dto.OutboundAckResponse
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.bot.dto.RoomPresenceDto
import org.taonity.sinairllmbot.bot.service.BotPresenceService
import org.taonity.sinairllmbot.bot.service.OutboundMessageService

/**
 * Internal endpoints the chat collector polls to deliver the bot's replies.
 * Permitted without auth (internal traffic) — see SecurityConfig.
 */
@RestController
@RequestMapping("/api/chat/outbound")
class BotOutboundController(
    private val outboundMessageService: OutboundMessageService,
    private val botPresenceService: BotPresenceService,
) {
    @GetMapping
    fun claim(
        @RequestParam(required = false) room: String?,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<OutboundMessageDto> = outboundMessageService.claimPending(room, limit)

    @PostMapping("/ack")
    fun ack(@RequestBody request: OutboundAckRequest): OutboundAckResponse =
        OutboundAckResponse(outboundMessageService.acknowledge(request.ids))

    /** Current online/offline presence per configured room, so the collector can reflect it in chat. */
    @GetMapping("/presence")
    fun presence(): List<RoomPresenceDto> = botPresenceService.allPresences()
}
