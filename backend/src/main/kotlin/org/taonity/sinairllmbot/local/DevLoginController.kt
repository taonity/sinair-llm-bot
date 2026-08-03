package org.taonity.sinairllmbot.local

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dev")
@Profile("stub-google")
class DevLoginController(
    private val devLoginProperties: DevLoginProperties,
) {
    @GetMapping("/stub-users")
    fun stubUsers(): List<DevLoginProperties.StubLogin> = devLoginProperties.stubLogins
}
