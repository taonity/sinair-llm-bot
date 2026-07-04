package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistent per-room bot control state so `!stop`/`!start` (muted) and `!sleep`/`!wake` (asleep)
 * survive a backend restart. The room target is the natural key.
 */
@Entity
@Table(name = "room_bot_state")
class RoomBotStateEntity(
    @Id
    val roomTarget: String,
    var muted: Boolean = false,
    var asleep: Boolean = false,
    var updatedAt: Instant = Instant.now(),
)
