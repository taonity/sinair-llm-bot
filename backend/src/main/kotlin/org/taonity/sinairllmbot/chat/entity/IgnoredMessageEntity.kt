package org.taonity.sinairllmbot.chat.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Tombstone for a message that was intentionally ignored (dropped while the room was asleep).
 *
 * Only the [dedupKey] is retained so the same message replayed later (e.g. in the history burst
 * after a reconnect) is recognised and skipped instead of leaking into [ChatMessageEntity].
 */
@Entity
@Table(name = "ignored_message")
class IgnoredMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val dedupKey: String,
    val roomTarget: String,
    val createdAt: Instant = Instant.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IgnoredMessageEntity) return false
        return dedupKey == other.dedupKey
    }

    override fun hashCode(): Int = dedupKey.hashCode()
}
