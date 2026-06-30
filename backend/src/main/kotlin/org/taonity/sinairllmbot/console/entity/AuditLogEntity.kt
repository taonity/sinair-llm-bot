package org.taonity.sinairllmbot.console.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * An immutable record of a change made through the data console.
 *
 * By design this entity stores no business data values — only the action, the type and id of the
 * affected record, who performed it and when. Entries are purged after the retention window.
 */
@Entity
@Table(name = "audit_log")
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: AuditAction,
    /** The kind of record affected, e.g. "chat_message", "room_summary", "access_request". */
    @Column(name = "target_type", nullable = false)
    val targetType: String,
    /** Identifier of the affected record. Never contains the record's contents. */
    @Column(name = "target_id")
    val targetId: String? = null,
    @Column(name = "actor_google_id", nullable = false)
    val actorGoogleId: String,
    @Column(name = "actor_email", nullable = false)
    val actorEmail: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),
)
