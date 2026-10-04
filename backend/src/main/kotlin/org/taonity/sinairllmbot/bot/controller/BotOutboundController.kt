package org.taonity.sinairllmbot.bot.controller

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.bot.dto.OutboundAckRequest
import org.taonity.sinairllmbot.bot.dto.OutboundAckResponse
import org.taonity.sinairllmbot.bot.dto.OutboundMessageDto
import org.taonity.sinairllmbot.bot.dto.NicknameUpdateRequest
import org.taonity.sinairllmbot.bot.dto.RoomPresenceDto
import org.taonity.sinairllmbot.bot.service.BotNicknameService
import org.taonity.sinairllmbot.bot.service.BotPresenceService
import org.taonity.sinairllmbot.bot.service.BotTypingService
import org.taonity.sinairllmbot.bot.service.OutboundMessageService
import org.taonity.sinairllmbot.bot.metrics.BotStatusMetrics
import org.taonity.sinairllmbot.observability.logging.EndpointLogLevel
import org.taonity.sinairllmbot.observability.logging.LogLevel

@RestController
@RequestMapping("/api/chat/outbound")
class BotOutboundController(
    private val outboundMessageService: OutboundMessageService,
    private val botNicknameService: BotNicknameService,
    private val botPresenceService: BotPresenceService,
    private val botTypingService: BotTypingService,
    private val botStatusMetrics: BotStatusMetrics,
) {
    data class CollectorStatusRequest(
        @field:Size(max = 100) val rooms: Set<@Size(max = 200) String>,
        val sendingEnabled: Boolean,
    )

    @EndpointLogLevel(LogLevel.TRACE)
    @PostMapping("/collector-status")
    fun collectorStatus(@Valid @RequestBody request: CollectorStatusRequest) =
        botStatusMetrics.collectorHeartbeat(request.rooms, request.sendingEnabled)

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
    @PostMapping("/presence/nickname")
    fun updateNickname(@RequestBody request: NicknameUpdateRequest) =
        botNicknameService.update(request.nickname, "collector")

    @EndpointLogLevel(LogLevel.TRACE)
    @GetMapping("/typing")
    fun typing(): List<String> = botTypingService.typingRooms()
}
