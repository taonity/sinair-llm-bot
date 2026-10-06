package org.taonity.sinairllmbot.bot.service

import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.taonity.sinairllmbot.bot.repository.OutboundMessageRepository
import org.taonity.sinairllmbot.bot.repository.PendingBotMessageRepository
import org.taonity.sinairllmbot.chat.entity.ChatMessageEntity
import org.taonity.sinairllmbot.chat.repository.ChatMessageRepository
import java.time.Instant

class PendingBotMessagesTest {
    @Test
    fun `messages sent before this backend started are never enqueued even when received now`() {
        val repository = mock(PendingBotMessageRepository::class.java)
        val pending = PendingBotMessages(repository, mock(ChatMessageRepository::class.java), mock(OutboundMessageRepository::class.java))
        val oldMessage = ChatMessageEntity(
            id = "old", dedupKey = "ext:old", roomTarget = "#room", senderMemberId = 1,
            senderLogin = "user", messageText = "Old question", messageStyle = "message",
            sentAt = Instant.EPOCH, receivedAt = Instant.now(),
        )

        pending.enqueue(listOf(oldMessage), 0)

        verify(repository, never()).save(any())
    }
}