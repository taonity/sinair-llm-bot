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

@Entity
@Table(name = "audit_log")
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: AuditAction,
    @Column(name = "target_type", nullable = false)
    val targetType: String,
    @Column(name = "target_id")
    val targetId: String? = null,
    @Column(name = "actor_google_id", nullable = false)
    val actorGoogleId: String,
    @Column(name = "actor_email", nullable = false)
    val actorEmail: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant = Instant.now(),
)
