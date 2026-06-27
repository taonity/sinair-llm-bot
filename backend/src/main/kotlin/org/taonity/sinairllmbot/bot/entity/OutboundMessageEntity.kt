package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A reply produced by the bot, waiting to be delivered to the chat by the collector.
 *
 * Lifecycle: PENDING -> CLAIMED (collector fetched it) -> SENT (collector confirmed delivery).
 */
@Entity
@Table(name = "outbound_message")
class OutboundMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val roomTarget: String,
    val messageText: String,
    /** External id of the message this reply reacts to, for traceability. */
    val replyToExternalId: String? = null,
    @Enumerated(EnumType.STRING)
    var status: OutboundStatus = OutboundStatus.PENDING,
    val createdAt: Instant = Instant.now(),
    var claimedAt: Instant? = null,
    var sentAt: Instant? = null,
)

enum class OutboundStatus {
    PENDING,
    CLAIMED,
    SENT,
}
