package org.taonity.sinairllmbot.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.taonity.sinairllmbot.config.entity.BotConfigTierEntity
import org.taonity.sinairllmbot.config.repository.BotConfigTierRepository
import org.taonity.sinairllmbot.config.service.ConfigService
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.taonity.sinairllmbot.security.principal.SafeGoogleUserInfo
import org.taonity.sinairllmbot.user.entity.UserEntity
import org.taonity.sinairllmbot.user.repository.UserRepository

@SpringBootTest
@ActiveProfiles("h2")
class ConfigTierProvenanceTest {
    @Autowired
    private lateinit var settings: BotSettings

    @Autowired
    private lateinit var configService: ConfigService

    @Autowired
    private lateinit var tierRepository: BotConfigTierRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `database tier shadowing deployed tier is visible and can be reset`() {
        val userId = "config-tier-owner"
        val principal = GoogleUserPrincipal(
            emptyList(),
            emptyMap(),
            SafeGoogleUserInfo(userId, "owner@example.com", "Owner", null),
            userId,
        )
        userRepository.save(UserEntity(userId, "owner@example.com", "Owner").grantOwner())

        val deployedTier = settings.deployedDefaults().llm.tier("cheap")
        val databaseTemperature = deployedTier.temperature + 0.25
        tierRepository.save(
            BotConfigTierEntity(
                name = "cheap",
                model = deployedTier.model,
                temperature = databaseTemperature,
                maxTokens = deployedTier.maxTokens,
                createdBy = "owner@example.com",
            ),
        )
        settings.reload()

        try {
            val schema = configService.getSchema(principal)
            val temperature = schema.fields.single { it.key == "app.llm.tiers.cheap.temperature" }
            val tier = schema.tiers.single { it.name == "cheap" }

            assertThat(temperature.defaultValue).isEqualTo(deployedTier.temperature)
            assertThat(temperature.value).isEqualTo(databaseTemperature)
            assertThat(temperature.overridden).isTrue()
            assertThat(temperature.resettable).isFalse()
            assertThat(tier.definedInProperties).isTrue()
            assertThat(tier.definedInDatabase).isTrue()
            assertThat(tier.shadowsDeployedTier).isTrue()

            val resetSchema = configService.deleteTier(principal, "cheap")
            val resetTemperature = resetSchema.fields.single { it.key == "app.llm.tiers.cheap.temperature" }

            assertThat(resetTemperature.value).isEqualTo(deployedTier.temperature)
            assertThat(resetTemperature.overridden).isFalse()
            assertThat(tierRepository.existsById("cheap")).isFalse()
        } finally {
            tierRepository.deleteById("cheap")
            userRepository.deleteById(userId)
            settings.reload()
        }
    }
}