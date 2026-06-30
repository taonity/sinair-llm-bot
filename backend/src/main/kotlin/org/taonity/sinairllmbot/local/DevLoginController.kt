package org.taonity.sinairllmbot.local

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Dev-only endpoint exposing the stub login shortcuts. Exists only under the `stub-google` profile,
 * so it is absent in production. Permitted without authentication so the login page can show the
 * options before a session exists.
 */
@RestController
@RequestMapping("/dev")
@Profile("stub-google")
class DevLoginController(
    private val devLoginProperties: DevLoginProperties,
) {
    @GetMapping("/stub-users")
    fun stubUsers(): List<DevLoginProperties.StubLogin> = devLoginProperties.stubLogins
}
