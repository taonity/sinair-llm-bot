package org.example.fullstackstarter.hello.controller

import org.example.fullstackstarter.security.principal.GoogleUserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(@AuthenticationPrincipal principal: GoogleUserPrincipal): Map<String, String> {
        return mapOf(
            "message" to "Hello, ${principal.getName()}!",
            "email" to principal.getEmail()
        )
    }

    @GetMapping("/")
    fun root(): Map<String, String> {
        return mapOf("status" to "ok", "service" to "fullstack-starter")
    }
}
