package org.taonity.sinairllmbot.console.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.console.dto.AccessInfoResponse
import org.taonity.sinairllmbot.console.dto.PendingRequestDto
import org.taonity.sinairllmbot.console.dto.UserSummaryDto
import org.taonity.sinairllmbot.console.entity.AuditAction
import org.taonity.sinairllmbot.console.exception.ConsoleForbiddenException
import org.taonity.sinairllmbot.console.exception.ConsoleNotFoundException
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.taonity.sinairllmbot.user.entity.AccessRequestStatus
import org.taonity.sinairllmbot.user.entity.ConsoleRole
import org.taonity.sinairllmbot.user.repository.UserRepository

/**
 * Handles the access-request lifecycle: a freshly logged-in user has no access and must request
 * VIEWER or EDITOR access, which an admin then approves or rejects.
 */
@Service
class AccessService(
    private val userRepository: UserRepository,
    private val accessGuard: AccessGuard,
    private val auditService: AuditService,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun describe(principal: GoogleUserPrincipal): AccessInfoResponse {
        val user = accessGuard.currentUser(principal)
        return AccessInfoResponse(
            email = user.email,
            displayName = user.displayName,
            role = user.role,
            accessStatus = user.accessStatus,
            requestedRole = user.requestedRole,
            canView = user.role.canView(),
            canEdit = user.role.canEdit(),
            isAdmin = user.role.isAdmin(),
            isOwner = user.role.isOwner(),
        )
    }

    @Transactional
    fun requestAccess(principal: GoogleUserPrincipal, requestedRole: ConsoleRole): AccessInfoResponse {
        if (requestedRole != ConsoleRole.VIEWER && requestedRole != ConsoleRole.EDITOR) {
            throw ConsoleForbiddenException("Only VIEWER or EDITOR access can be requested")
        }
        val user = accessGuard.currentUser(principal)
        if (user.role.isAdmin()) {
            throw ConsoleForbiddenException("Admins already have full access")
        }
        if (user.role.rank() >= requestedRole.rank()) {
            throw ConsoleForbiddenException("You already have at least this level of access")
        }
        user.accessStatus = AccessRequestStatus.PENDING
        user.requestedRole = requestedRole
        auditService.record(AuditAction.REQUEST_ACCESS, "access_request", user.googleId, user)
        LOGGER.info { "User ${user.googleId} requested $requestedRole access" }
        return describe(principal)
    }

    fun listPendingRequests(principal: GoogleUserPrincipal): List<PendingRequestDto> {
        accessGuard.requireAdmin(principal)
        return userRepository.findByAccessStatusOrderByEmailAsc(AccessRequestStatus.PENDING)
            .map { PendingRequestDto(it.googleId, it.email, it.displayName, it.requestedRole) }
    }

    @Transactional
    fun approve(principal: GoogleUserPrincipal, targetGoogleId: String, grantedRole: ConsoleRole) {
        val admin = accessGuard.requireAdmin(principal)
        if (grantedRole != ConsoleRole.VIEWER && grantedRole != ConsoleRole.EDITOR) {
            throw ConsoleForbiddenException("Granted role must be VIEWER or EDITOR")
        }
        val target = userRepository.findById(targetGoogleId)
            .orElseThrow { ConsoleNotFoundException("User not found") }
        target.role = grantedRole
        target.accessStatus = AccessRequestStatus.APPROVED
        target.requestedRole = null
        auditService.record(AuditAction.APPROVE_ACCESS, "access_request", target.googleId, admin)
        LOGGER.info { "Admin ${admin.googleId} approved $grantedRole for ${target.googleId}" }
    }

    @Transactional
    fun reject(principal: GoogleUserPrincipal, targetGoogleId: String) {
        val admin = accessGuard.requireAdmin(principal)
        val target = userRepository.findById(targetGoogleId)
            .orElseThrow { ConsoleNotFoundException("User not found") }
        target.role = ConsoleRole.NONE
        target.accessStatus = AccessRequestStatus.REJECTED
        target.requestedRole = null
        auditService.record(AuditAction.REJECT_ACCESS, "access_request", target.googleId, admin)
        LOGGER.info { "Admin ${admin.googleId} rejected access for ${target.googleId}" }
    }

    fun listUsers(principal: GoogleUserPrincipal): List<UserSummaryDto> {
        accessGuard.requireAdmin(principal)
        return userRepository.findAllByOrderByEmailAsc().map {
            UserSummaryDto(it.googleId, it.email, it.displayName, it.role, it.accessStatus, it.requestedRole)
        }
    }

    @Transactional
    fun changeRole(
        principal: GoogleUserPrincipal,
        targetGoogleId: String,
        newRole: ConsoleRole,
    ): UserSummaryDto {
        val actor = accessGuard.requireAdmin(principal)
        val target = userRepository.findById(targetGoogleId)
            .orElseThrow { ConsoleNotFoundException("User not found") }
        if (target.googleId == actor.googleId) {
            throw ConsoleForbiddenException("You cannot change your own role")
        }
        // Granting/revoking ADMIN or OWNER, or touching an existing admin/owner, is owner-only.
        if (newRole.isAdmin() || target.role.isAdmin()) {
            if (!actor.role.isOwner()) {
                throw ConsoleForbiddenException("Only the owner can manage admin roles")
            }
        }
        target.role = newRole
        target.accessStatus =
            if (newRole == ConsoleRole.NONE) AccessRequestStatus.NONE else AccessRequestStatus.APPROVED
        target.requestedRole = null
        auditService.record(AuditAction.CHANGE_ROLE, "user", target.googleId, actor)
        LOGGER.info { "User ${actor.googleId} set role of ${target.googleId} to $newRole" }
        return UserSummaryDto(
            target.googleId,
            target.email,
            target.displayName,
            target.role,
            target.accessStatus,
            target.requestedRole,
        )
    }
}
