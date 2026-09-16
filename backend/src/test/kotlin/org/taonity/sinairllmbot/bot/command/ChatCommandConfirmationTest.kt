package org.taonity.sinairllmbot.bot.command

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.taonity.sinairllmbot.chat.entity.ChatEventEntity
import java.time.Instant

class ChatCommandConfirmationTest {
    @Test
    fun `only the requested change on the same member confirms an action`() {
        val event = ChatEventEntity(dedupKey = "event", roomTarget = "#room", memberId = 7, memberName = "new", status = "nick_change", eventTime = Instant.EPOCH)
        assertThat(ChatCommandConfirmation.matches(event, 7, "nick", "new")).isTrue()
        assertThat(ChatCommandConfirmation.matches(event, 8, "nick", "new")).isFalse()
        assertThat(ChatCommandConfirmation.matches(event, null, "nick", "new")).isFalse()
        assertThat(ChatCommandConfirmation.matches(event, 7, "nick", "other")).isFalse()
        assertThat(ChatCommandConfirmation.matches(event, 7, "kick", "someone")).isFalse()
    }
}