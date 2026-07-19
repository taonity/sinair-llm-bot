package org.taonity.sinairllmbot.console.controller

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
import org.taonity.sinairllmbot.console.dto.PipelineRunDto
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
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageResponse<ChatMessageDto> = consoleDataService.listChatMessages(principal, room, q, field, page, size, direction)

    @GetMapping("/chat-messages/{id}/page")
    fun locateChatMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageLocation = PageLocation(consoleDataService.locateChatMessagePage(principal, id, size, direction))

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
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageResponse<ChatEventDto> = consoleDataService.listChatEvents(principal, room, q, field, page, size, direction)

    @GetMapping("/chat-events/{id}/page")
    fun locateChatEvent(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageLocation = PageLocation(consoleDataService.locateChatEventPage(principal, id, size, direction))

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
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageResponse<OutboundMessageDto> = consoleDataService.listOutboundMessages(principal, room, q, field, page, size, direction)

    @GetMapping("/outbound-messages/{id}/page")
    fun locateOutboundMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageLocation = PageLocation(consoleDataService.locateOutboundMessagePage(principal, id, size, direction))

    @DeleteMapping("/outbound-messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteOutboundMessage(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deleteOutboundMessage(principal, id)

    @GetMapping("/pipeline-runs")
    fun pipelineRuns(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestParam(required = false) room: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) field: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageResponse<PipelineRunDto> = consoleDataService.listPipelineRuns(principal, room, q, field, page, size, direction)

    @GetMapping("/pipeline-runs/{id}/page")
    fun locatePipelineRun(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "desc") direction: String,
    ): PageLocation = PageLocation(consoleDataService.locatePipelineRunPage(principal, id, size, direction))

    @GetMapping("/pipeline-runs/{id}/llm-usage/{index}/{kind}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun pipelineRunLlmPayload(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
        @PathVariable index: Int,
        @PathVariable kind: String,
    ): String = consoleDataService.pipelineRunLlmPayload(principal, id, index, kind)

    @DeleteMapping("/pipeline-runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePipelineRun(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deletePipelineRun(principal, id)

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

    @DeleteMapping("/summaries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSummary(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable id: String,
    ) = consoleDataService.deleteSummary(principal, id)

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
