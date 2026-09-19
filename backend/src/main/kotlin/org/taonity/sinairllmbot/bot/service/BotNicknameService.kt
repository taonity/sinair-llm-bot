package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.ConfigValidationException
import org.taonity.sinairllmbot.config.entity.BotConfigOverrideEntity
import org.taonity.sinairllmbot.config.repository.BotConfigOverrideRepository
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Service
class BotNicknameService(
    private val objectMapper: ObjectMapper,
    private val settings: BotSettings,
    private val overrideRepository: BotConfigOverrideRepository,
) {
    @Transactional
    fun update(nickname: String, updatedBy: String) {
        val normalizedNickname = nickname.trim()
        if (normalizedNickname.isBlank()) throw ConfigValidationException("Nickname must not be blank")

        val now = Instant.now()
        val existing = overrideRepository.findById(CONFIG_KEY).orElse(null)
        if (existing != null) {
            existing.valueJson = objectMapper.writeValueAsString(normalizedNickname)
            existing.updatedAt = now
            existing.updatedBy = updatedBy
            overrideRepository.save(existing)
        } else {
            overrideRepository.save(
                BotConfigOverrideEntity(
                    configKey = CONFIG_KEY,
                    valueJson = objectMapper.writeValueAsString(normalizedNickname),
                    updatedAt = now,
                    updatedBy = updatedBy,
                ),
            )
        }
        settings.reload()
        LOGGER.info { "Synced $CONFIG_KEY config to '$normalizedNickname'" }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        const val CONFIG_KEY = "app.bot.persona.name"
    }
}