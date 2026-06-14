package org.taonity.sinairllmbot.chat.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_event")
class ChatEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val dedupKey: String,
    val roomTarget: String,
    val memberId: Int,
    val userId: Int = 0,
    val memberName: String,
    val memberColor: String? = null,
    val status: String,
    val eventData: String? = null,
    val isGirl: Boolean = false,
    val isModer: Boolean = false,
    val isOwner: Boolean = false,
    val eventTime: Instant,
    val receivedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatEventEntity) return false
        return dedupKey == other.dedupKey
    }

    override fun hashCode(): Int = dedupKey.hashCode()
}
