package org.taonity.sinairllmbot.chat.entity

import jakarta.persistence.Entity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
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
    val receivedAt: Instant = Instant.now(),
    val sourceOutboundMessageId: String? = null,
    val sourceOutboundMatch: String? = null,
    @Embedded
    var botResponse: BotResponseState? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessageEntity) return false
        return dedupKey == other.dedupKey
    }

    override fun hashCode(): Int = dedupKey.hashCode()
}

@Embeddable
data class BotResponseState(
    @Column(name = "bot_response_status")
    val status: String,
    @Column(name = "bot_response_reason")
    val reason: String,
    @Column(name = "bot_response_detail", columnDefinition = "text")
    val detail: String? = null,
    @Column(name = "bot_response_category")
    val category: String? = null,
    @Column(name = "bot_response_deferred_at")
    val deferredAt: Instant? = null,
    @Column(name = "bot_response_next_attempt_at")
    val nextAttemptAt: Instant? = null,
    @Column(name = "bot_response_updated_at")
    val updatedAt: Instant = Instant.now(),
    @Column(name = "bot_response_outbound_message_id")
    val outboundMessageId: String? = null,
)
