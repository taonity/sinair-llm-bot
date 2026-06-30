package org.taonity.sinairllmbot.console.service

import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.console.exception.ConsoleForbiddenException
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.taonity.sinairllmbot.user.entity.UserEntity
import org.taonity.sinairllmbot.user.repository.UserRepository

/**
 * Resolves the current user and enforces console role requirements. Throws
 * [ConsoleForbiddenException] (mapped to HTTP 403) when the user lacks the required role.
 */
@Service
class AccessGuard(
    private val userRepository: UserRepository,
) {
    fun currentUser(principal: GoogleUserPrincipal): UserEntity =
        userRepository.findById(principal.getGoogleId())
            .orElseThrow { ConsoleForbiddenException("Unknown user") }

    fun requireView(principal: GoogleUserPrincipal): UserEntity {
        val user = currentUser(principal)
        if (!user.role.canView()) {
            throw ConsoleForbiddenException("Console view access required")
        }
        return user
    }

    fun requireEdit(principal: GoogleUserPrincipal): UserEntity {
        val user = currentUser(principal)
        if (!user.role.canEdit()) {
            throw ConsoleForbiddenException("Console edit access required")
        }
        return user
    }

    fun requireAdmin(principal: GoogleUserPrincipal): UserEntity {
        val user = currentUser(principal)
        if (!user.role.isAdmin()) {
            throw ConsoleForbiddenException("Console admin access required")
        }
        return user
    }

    fun requireOwner(principal: GoogleUserPrincipal): UserEntity {
        val user = currentUser(principal)
        if (!user.role.isOwner()) {
            throw ConsoleForbiddenException("Console owner access required")
        }
        return user
    }
}
