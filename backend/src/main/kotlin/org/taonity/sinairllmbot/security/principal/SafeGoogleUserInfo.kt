package org.taonity.sinairllmbot.security.principal

import com.google.api.services.oauth2.model.Userinfo
import java.io.Serializable

class SafeGoogleUserInfo(
    val id: String,
    val email: String,
    val displayName: String,
    val pictureUrl: String?
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        fun fromApi(userinfo: Userinfo): SafeGoogleUserInfo {
            return SafeGoogleUserInfo(
                id = requireNotNull(userinfo.id) { "Google user ID must not be null" },
                email = requireNotNull(userinfo.email) { "Google user email must not be null" },
                displayName = userinfo.name ?: userinfo.email,
                pictureUrl = userinfo.picture
            )
        }
    }
}
