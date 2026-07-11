package org.taonity.sinairllmbot.security.principal

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import java.io.Serializable

class GoogleUserPrincipal(
    private val authorities: Collection<GrantedAuthority>,
    private val attributes: Map<String, Any>,
    val safeGoogleUserInfo: SafeGoogleUserInfo,
    private val nameAttributeKey: String,
    private val idToken: OidcIdToken? = null,
    private val userInfo: OidcUserInfo? = null
) : OidcUser, Serializable {

    override fun getName(): String = nameAttributeKey

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getIdToken(): OidcIdToken = idToken ?: OidcIdToken("empty", null, null, mapOf("sub" to getGoogleId()))

    override fun getUserInfo(): OidcUserInfo? = userInfo

    override fun getClaims(): Map<String, Any> = attributes

    fun getGoogleId(): String = safeGoogleUserInfo.id

    override fun getEmail(): String = safeGoogleUserInfo.email

    fun getDisplayName(): String = safeGoogleUserInfo.displayName

    fun getPictureUrl(): String? = safeGoogleUserInfo.pictureUrl

    companion object {
        private const val serialVersionUID: Long = 1L

        fun of(safeGoogleUserInfo: SafeGoogleUserInfo, oAuth2User: OAuth2User): GoogleUserPrincipal {
            val idToken = if (oAuth2User is OidcUser) oAuth2User.idToken else null
            val userInfo = if (oAuth2User is OidcUser) oAuth2User.userInfo else null
            return GoogleUserPrincipal(
                oAuth2User.authorities,
                oAuth2User.attributes,
                safeGoogleUserInfo,
                safeGoogleUserInfo.displayName,
                idToken,
                userInfo
            )
        }
    }
}
