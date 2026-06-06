package org.example.fullstackstarter.security.service

import com.google.api.services.oauth2.model.Userinfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.fullstackstarter.security.principal.GoogleUserPrincipal
import org.example.fullstackstarter.security.principal.SafeGoogleUserInfo
import org.example.fullstackstarter.user.service.UserService
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class OidcUserPersistenceService(
    private val userService: UserService
) : OidcUserService() {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun loadUser(userRequest: OidcUserRequest?): OidcUser {
        val validatedUserRequest = requireNotNull(userRequest) { "OidcUserRequest must not be null" }

        val oidcUser: OidcUser = try {
            super.loadUser(validatedUserRequest)
        } catch (e: OAuth2AuthenticationException) {
            LOGGER.error(e) { "OIDC user loading failed" }
            throw e
        }

        val userinfo = toUserinfo(oidcUser.attributes)
        val safeGoogleUserInfo = SafeGoogleUserInfo.fromApi(userinfo)
        val principal = GoogleUserPrincipal.of(safeGoogleUserInfo, oidcUser)
        userService.createOrUpdateUser(principal)
        return principal
    }

    private fun toUserinfo(attributes: Map<String, Any>): Userinfo {
        val userinfo = Userinfo()
        userinfo.id = attributes["sub"] as? String
        userinfo.email = attributes["email"] as? String
        userinfo.name = attributes["name"] as? String
        userinfo.givenName = attributes["given_name"] as? String
        userinfo.familyName = attributes["family_name"] as? String
        userinfo.picture = attributes["picture"] as? String
        userinfo.verifiedEmail = attributes["email_verified"] as? Boolean
        return userinfo
    }
}
