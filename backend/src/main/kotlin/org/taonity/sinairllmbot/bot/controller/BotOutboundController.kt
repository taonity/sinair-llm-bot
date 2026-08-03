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
import org.taonity.sinairllmbot.bot.service.BotTypingService
import org.taonity.sinairllmbot.bot.service.OutboundMessageService
import org.taonity.sinairllmbot.observability.logging.EndpointLogLevel
import org.taonity.sinairllmbot.observability.logging.LogLevel

@RestController
@RequestMapping("/api/chat/outbound")
class BotOutboundController(
    private val outboundMessageService: OutboundMessageService,
    private val botPresenceService: BotPresenceService,
    private val botTypingService: BotTypingService,
) {
    @EndpointLogLevel(LogLevel.TRACE)
    @GetMapping
    fun claim(
        @RequestParam(required = false) room: String?,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<OutboundMessageDto> = outboundMessageService.claimPending(room, limit)

    @EndpointLogLevel(LogLevel.TRACE)
    @PostMapping("/ack")
    fun ack(@RequestBody request: OutboundAckRequest): OutboundAckResponse =
        OutboundAckResponse(outboundMessageService.acknowledge(request.ids))

    @EndpointLogLevel(LogLevel.TRACE)
    @GetMapping("/presence")
    fun presence(): List<RoomPresenceDto> = botPresenceService.allPresences()

    @EndpointLogLevel(LogLevel.TRACE)
    @GetMapping("/typing")
    fun typing(): List<String> = botTypingService.typingRooms()
}
