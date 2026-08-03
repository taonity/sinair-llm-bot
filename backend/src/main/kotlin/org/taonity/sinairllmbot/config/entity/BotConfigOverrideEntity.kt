package org.taonity.sinairllmbot.config.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "bot_config_override")
class BotConfigOverrideEntity(
    @Id
    @Column(name = "config_key", nullable = false, length = 200)
    val configKey: String,
    @Column(name = "value_json", nullable = false, length = 20000)
    var valueJson: String,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "updated_by", nullable = false)
    var updatedBy: String,
)
