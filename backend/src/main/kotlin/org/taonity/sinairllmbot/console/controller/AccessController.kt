package org.taonity.sinairllmbot.console.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.console.dto.AccessInfoResponse
import org.taonity.sinairllmbot.console.dto.AccessRequestBody
import org.taonity.sinairllmbot.console.dto.ApproveAccessBody
import org.taonity.sinairllmbot.console.dto.PendingRequestDto
import org.taonity.sinairllmbot.console.service.AccessService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal

@RestController
@RequestMapping("/console/access")
class AccessController(
    private val accessService: AccessService,
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: GoogleUserPrincipal): AccessInfoResponse =
        accessService.describe(principal)

    @PostMapping("/request")
    fun request(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestBody body: AccessRequestBody,
    ): AccessInfoResponse = accessService.requestAccess(principal, body.requestedRole)

    @GetMapping("/requests")
    fun pendingRequests(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
    ): List<PendingRequestDto> = accessService.listPendingRequests(principal)

    @PostMapping("/requests/{googleId}/approve")
    fun approve(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable googleId: String,
        @RequestBody body: ApproveAccessBody,
    ) = accessService.approve(principal, googleId, body.role)

    @PostMapping("/requests/{googleId}/reject")
    fun reject(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable googleId: String,
    ) = accessService.reject(principal, googleId)
}
