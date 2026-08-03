package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "room_summary_history")
class RoomSummaryHistoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val roomTarget: String,
    // Plain String with columnDefinition = "text" for the same reasons as RoomSummaryEntity.summary.
    @Column(columnDefinition = "text")
    var summary: String,
    val messageCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    var pipelineRunId: String? = null,
)
