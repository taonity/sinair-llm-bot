package org.taonity.sinairllmbot.config.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bot_config_tier")
class BotConfigTierEntity(
    @Id
    @Column(name = "name", nullable = false, length = 50)
    val name: String,
    @Column(name = "model", nullable = false, length = 200)
    var model: String,
    @Column(name = "temperature", nullable = false)
    var temperature: Double,
    @Column(name = "max_tokens", nullable = false)
    var maxTokens: Int,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "created_by", nullable = false)
    var createdBy: String,
)
