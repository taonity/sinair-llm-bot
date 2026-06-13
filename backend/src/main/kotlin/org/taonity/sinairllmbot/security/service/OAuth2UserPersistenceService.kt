package org.taonity.sinairllmbot.security.service

import com.google.api.services.oauth2.model.Userinfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.taonity.sinairllmbot.security.principal.SafeGoogleUserInfo
import org.taonity.sinairllmbot.user.service.UserService
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class OAuth2UserPersistenceService(
    private val userService: UserService
) : DefaultOAuth2UserService() {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override fun loadUser(userRequest: OAuth2UserRequest?): OAuth2User {
        val validatedUserRequest = requireNotNull(userRequest) { "OAuth2UserRequest must not be null" }

        val oAuth2User: OAuth2User = try {
            super.loadUser(validatedUserRequest)
        } catch (e: OAuth2AuthenticationException) {
            LOGGER.error(e) { "OAuth2 user loading failed" }
            throw e
        }

        val userinfo = toUserinfo(oAuth2User.attributes)
        val safeGoogleUserInfo = SafeGoogleUserInfo.fromApi(userinfo)
        val principal = GoogleUserPrincipal.of(safeGoogleUserInfo, oAuth2User)
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
