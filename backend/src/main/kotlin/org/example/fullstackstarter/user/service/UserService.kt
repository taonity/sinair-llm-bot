package org.example.fullstackstarter.user.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.fullstackstarter.security.principal.GoogleUserPrincipal
import org.example.fullstackstarter.user.entity.UserEntity
import org.example.fullstackstarter.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    @Transactional
    fun createOrUpdateUser(principal: GoogleUserPrincipal) {
        val existing = userRepository.findById(principal.getGoogleId()).orElse(null)
        if (existing != null) {
            existing.updateDetails(principal.getDisplayName(), principal.getEmail(), principal.getPictureUrl())
            LOGGER.debug { "Updated user: ${existing.googleId}" }
        } else {
            val newUser = UserEntity(
                googleId = principal.getGoogleId(),
                email = principal.getEmail(),
                displayName = principal.getDisplayName(),
                pictureUrl = principal.getPictureUrl()
            )
            userRepository.save(newUser)
            LOGGER.info { "Created new user: ${newUser.googleId}" }
        }
    }
}
