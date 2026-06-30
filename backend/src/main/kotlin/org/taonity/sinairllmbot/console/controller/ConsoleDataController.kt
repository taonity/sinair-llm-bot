package org.taonity.sinairllmbot.console.controller

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.console.dto.AuditLogDto
import org.taonity.sinairllmbot.console.dto.ChatEventDto
import org.taonity.sinairllmbot.console.dto.ChatMessageDto
import org.taonity.sinairllmbot.console.dto.OutboundMessageDto
import org.taonity.sinairllmbot.console.dto.PageResponse
import org.taonity.sinairllmbot.console.dto.RoomSummaryDto
import org.taonity.sinairllmbot.console.dto.UpdateSummaryBody
import org.taonity.sinairllmbot.console.service.ConsoleDataService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal

@RestController
@RequestMapping("/console")
class ConsoleDataController(
    private val consoleDataService: ConsoleDataService,
) {
    @GetMapping("/chat-messages")
    fun chatMessages(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestParam(required = false) room: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) field: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<ChatMessageDto> = consoleDataService.listChatMessages(principal, room, q, field, page, size)

    @GetMapping("/chat-messages/{id}/page")
    fun locateChatMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageLocation = PageLocation(consoleDataService.locateChatMessagePage(principal, id, size))

    @DeleteMapping("/chat-messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteChatMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deleteChatMessage(principal, id)

    @GetMapping("/chat-events")
    fun chatEvents(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestParam(required = false) room: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) field: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<ChatEventDto> = consoleDataService.listChatEvents(principal, room, q, field, page, size)

    @GetMapping("/chat-events/{id}/page")
    fun locateChatEvent(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageLocation = PageLocation(consoleDataService.locateChatEventPage(principal, id, size))

    @DeleteMapping("/chat-events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteChatEvent(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deleteChatEvent(principal, id)

    @GetMapping("/outbound-messages")
    fun outboundMessages(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestParam(required = false) room: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) field: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageResponse<OutboundMessageDto> = consoleDataService.listOutboundMessages(principal, room, q, field, page, size)

    @GetMapping("/outbound-messages/{id}/page")
    fun locateOutboundMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
    ): PageLocation = PageLocation(consoleDataService.locateOutboundMessagePage(principal, id, size))

    @DeleteMapping("/outbound-messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOutboundMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deleteOutboundMessage(principal, id)

    @GetMapping("/summaries")
    fun summaries(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
    ): List<RoomSummaryDto> = consoleDataService.listSummaries(principal)

    @PutMapping("/summaries/{id}")
    fun updateSummary(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestBody body: UpdateSummaryBody,
    ): RoomSummaryDto = consoleDataService.updateSummary(principal, id, body.summary)

    @GetMapping("/audit-logs")
    fun auditLogs(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) field: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<AuditLogDto> = consoleDataService.listAuditLogs(principal, q, field, page, size)
}

data class PageLocation(val page: Int)
