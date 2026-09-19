package org.taonity.sinairllmbot.bot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.taonity.sinairllmbot.config.BotSettings
import org.taonity.sinairllmbot.config.entity.BotConfigOverrideEntity
import org.taonity.sinairllmbot.config.repository.BotConfigOverrideRepository
import tools.jackson.databind.ObjectMapper
import java.util.Optional

class BotNicknameServiceTest {
    private val objectMapper = mock(ObjectMapper::class.java)
    private val settings = mock(BotSettings::class.java)
    private val repository = mock(BotConfigOverrideRepository::class.java)
    private val service = BotNicknameService(objectMapper, settings, repository)

    @Test
    fun `persists normalized nickname and reloads settings`() {
        `when`(repository.findById("app.bot.persona.name")).thenReturn(Optional.empty())
        `when`(objectMapper.writeValueAsString("segfault_2")).thenReturn("\"segfault_2\"")

        service.update("  segfault_2  ", "collector")

        val captor = ArgumentCaptor.forClass(BotConfigOverrideEntity::class.java)
        verify(repository).save(captor.capture())
        assertThat(captor.value.configKey).isEqualTo("app.bot.persona.name")
        assertThat(captor.value.valueJson).isEqualTo("\"segfault_2\"")
        assertThat(captor.value.updatedBy).isEqualTo("collector")
        verify(settings).reload()
    }
}