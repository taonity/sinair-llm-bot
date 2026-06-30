package org.taonity.sinairllmbot.local

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Dev-only configuration listing the stub OAuth2 registrations the frontend can offer as
 * "log in as" shortcuts. Only populated under the local `stub-google` profile.
 */
@ConfigurationProperties(prefix = "app.dev")
data class DevLoginProperties(
    val stubLogins: List<StubLogin> = emptyList(),
) {
    data class StubLogin(
        val registrationId: String = "",
        val label: String = "",
    )
}
