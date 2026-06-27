package org.taonity.sinairllmbot.chat.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_message")
class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val dedupKey: String,
    val roomTarget: String,
    val senderMemberId: Int,
    val senderUserId: Int = 0,
    val senderLogin: String,
    val senderColor: String? = null,
    val messageText: String,
    val messageStyle: String,
    val recipientMemberId: Int = 0,
    val sentAt: Instant,
    val receivedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessageEntity) return false
        return dedupKey == other.dedupKey
    }

    override fun hashCode(): Int = dedupKey.hashCode()
}
