package org.taonity.sinairllmbot.console.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.console.dto.ChangeRoleBody
import org.taonity.sinairllmbot.console.dto.UserSummaryDto
import org.taonity.sinairllmbot.console.service.AccessService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal

/**
 * Admin user management. Listing requires ADMIN; changing roles to/from ADMIN or OWNER requires
 * OWNER (enforced in [AccessService.changeRole]).
 */
@RestController
@RequestMapping("/console/users")
class UserAdminController(
    private val accessService: AccessService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
    ): List<UserSummaryDto> = accessService.listUsers(principal)

    @PutMapping("/{googleId}/role")
    fun changeRole(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable googleId: String,
        @RequestBody body: ChangeRoleBody,
    ): UserSummaryDto = accessService.changeRole(principal, googleId, body.role)
}
