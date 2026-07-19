package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A superseded snapshot of a room's rolling summary. Each refresh archives the previous summary
 * here before overwriting the current [RoomSummaryEntity], so the console can show a short history.
 * Pruned to the latest few versions per room (see RoomSummaryService).
 */
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
    /** Total messages seen in the room when this version was current. */
    val messageCount: Int = 0,
    /** When this version was superseded (i.e. archived). */
    val createdAt: Instant = Instant.now(),
    /**
     * The pipeline run that produced this version (holds the source transcript). Purged by retention
     * after 7 days; this archived summary text is kept.
     */
    var pipelineRunId: String? = null,
)
