package org.taonity.sinairllmbot.config.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.taonity.sinairllmbot.config.dto.ConfigSchemaDto
import org.taonity.sinairllmbot.config.dto.CreateTierBody
import org.taonity.sinairllmbot.config.dto.UpdateConfigBody
import org.taonity.sinairllmbot.config.service.ConfigService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal

/**
 * Admin-only console endpoints for tuning LLM/bot configuration. All return the full schema
 * (metadata + default + current effective value per field, plus the tier list) so the UI can
 * re-render after a change.
 */
@RestController
@RequestMapping("/console/config")
class ConfigController(
    private val configService: ConfigService,
) {
    @GetMapping
    fun schema(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
    ): ConfigSchemaDto = configService.getSchema(principal)

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestBody body: UpdateConfigBody,
    ): ConfigSchemaDto = configService.update(principal, body.values)

    @DeleteMapping("/{key}")
    fun reset(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable key: String,
    ): ConfigSchemaDto = configService.reset(principal, key)

    @PostMapping("/tiers")
    fun addTier(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @RequestBody body: CreateTierBody,
    ): ConfigSchemaDto = configService.addTier(principal, body)

    @DeleteMapping("/tiers/{name}")
    fun deleteTier(
        @AuthenticationPrincipal principal: GoogleUserPrincipal,
        @PathVariable name: String,
    ): ConfigSchemaDto = configService.deleteTier(principal, name)
}
