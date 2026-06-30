package org.taonity.sinairllmbot.user.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.taonity.sinairllmbot.common.config.ConsoleProperties
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.taonity.sinairllmbot.user.entity.ConsoleRole
import org.taonity.sinairllmbot.user.entity.UserEntity
import org.taonity.sinairllmbot.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val consoleProperties: ConsoleProperties,
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun createOrUpdateUser(principal: GoogleUserPrincipal) {
        val isOwner = consoleProperties.isOwnerEmail(principal.getEmail())
        val isAdmin = consoleProperties.isAdminEmail(principal.getEmail())
        val existing = userRepository.findById(principal.getGoogleId()).orElse(null)
        if (existing != null) {
            existing.updateDetails(principal.getDisplayName(), principal.getEmail(), principal.getPictureUrl())
            if (isOwner && existing.role != ConsoleRole.OWNER) {
                existing.grantOwner()
                LOGGER.info { "Granted owner console access to bootstrapped user: ${existing.googleId}" }
            } else if (isAdmin && !existing.role.isAdmin()) {
                existing.grantAdmin()
                LOGGER.info { "Granted admin console access to bootstrapped user: ${existing.googleId}" }
            }
            LOGGER.debug { "Updated user: ${existing.googleId}" }
        } else {
            val newUser = UserEntity(
                googleId = principal.getGoogleId(),
                email = principal.getEmail(),
                displayName = principal.getDisplayName(),
                pictureUrl = principal.getPictureUrl()
            )
            if (isOwner) {
                newUser.grantOwner()
                LOGGER.info { "Bootstrapped owner console user: ${newUser.googleId}" }
            } else if (isAdmin) {
                newUser.grantAdmin()
                LOGGER.info { "Bootstrapped admin console user: ${newUser.googleId}" }
            }
            userRepository.save(newUser)
            LOGGER.info { "Created new user: ${newUser.googleId}" }
        }
    }
}
