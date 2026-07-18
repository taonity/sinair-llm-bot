package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "room_summary")
class RoomSummaryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    @Column(unique = true)
    val roomTarget: String,
    // Plain String (not @Lob): on Postgres @Lob on a String maps to a Large Object (oid) and fails
    // to read from the `text` column ("Bad value for type long"). The Flyway column is TEXT.
    var summary: String,
    /** Total messages seen in the room at the last refresh; drives the refresh cadence. */
    var messageCount: Int = 0,
    var updatedAt: Instant = Instant.now(),
)
