package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "pending_bot_message")
class PendingBotMessageEntity(
    @Id val messageId: String,
    val roomTarget: String,
    var availableAt: Instant,
    val createdAt: Instant,
)