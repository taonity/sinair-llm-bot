package org.taonity.sinairllmbot.config.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A user-created LLM tier added through the data console. The yaml `app.llm.tiers` provide the
 * built-in tiers; a row here contributes an additional tier whose base model/temperature/max-tokens
 * are seeded at creation time. Per-field edits afterwards are stored as `bot_config_override` rows
 * (`app.llm.tiers.<name>.<field>`), so this row acts as the tier's "default" that a reset reverts to.
 */
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
