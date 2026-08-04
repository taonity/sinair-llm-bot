package org.taonity.sinairllmbot.bot.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.taonity.sinairllmbot.bot.entity.OutboundStatus
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository

class BotTypingServiceTest {
    private val repository = mock(OutboundMessageRepository::class.java)
    private val service = BotTypingService(repository)

    @Test
    fun `keeps room typing until all active generations finish`() {
        `when`(repository.findDistinctRoomTargetsByStatus(OutboundStatus.PENDING)).thenReturn(emptyList())

        service.markTyping("room")
        service.markTyping("room")

        repeat(3) { assertEquals(listOf("room"), service.typingRooms()) }
        service.clearTyping("room")
        assertEquals(listOf("room"), service.typingRooms())
        service.clearTyping("room")
        assertEquals(emptyList<String>(), service.typingRooms())
    }
}