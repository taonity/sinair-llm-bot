package org.taonity.sinairllmbot.config.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bot_config_revision")
class BotConfigRevisionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    @Column(unique = true, length = 64)
    val contentHash: String,
    @Column(columnDefinition = "text")
    val effectiveConfigJson: String,
    val createdAt: Instant = Instant.now(),
)
