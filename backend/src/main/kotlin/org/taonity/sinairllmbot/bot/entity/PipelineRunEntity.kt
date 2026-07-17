package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/**
 * A persisted trace of one bot processing-pipeline run for a room, retained like chat data so the
 * console can show why the bot did (or did not) reply to a given message.
 *
 * [stagesJson] holds the ordered list of pipeline stages serialized as JSON (see
 * [org.taonity.sinairllmbot.bot.pipeline.PipelineStage]); keeping it as an opaque document keeps the
 * schema stable while new pipelines add their own stage shapes.
 */
@Entity
@Table(name = "pipeline_run")
class PipelineRunEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val pipelineKey: String,
    val roomTarget: String,
    val triggerMessageId: String? = null,
    val triggerSenderLogin: String,
    val triggerText: String,
    val outcome: String,
    val outcomeDetail: String? = null,
    val outboundMessageId: String? = null,
    @Lob
    val stagesJson: String,
    val createdAt: Instant = Instant.now(),
)
