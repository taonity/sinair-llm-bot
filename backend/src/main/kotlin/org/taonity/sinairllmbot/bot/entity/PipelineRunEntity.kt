package org.taonity.sinairllmbot.bot.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
    // Mapped as plain String (not @Lob): on Postgres @Lob on a String becomes a Large Object (oid),
    // which fails to read back from a `text` column ("Bad value for type long"). columnDefinition
    // = "text" keeps Hibernate-generated schemas (tests use ddl-auto=create-drop) from capping these
    // large JSON documents at VARCHAR(255); the Flyway column is already TEXT.
    @Column(columnDefinition = "text")
    val stagesJson: String,
    val totalTokens: Int = 0,
    @Column(columnDefinition = "text")
    val llmUsageJson: String = "[]",
    // How many times a JSON-only prompt (triage/critic) in this run returned output that couldn't be
    // deserialized (each retry is one failure), plus the offending payloads as a JSON array (see
    // [org.taonity.sinairllmbot.bot.pipeline.JsonParseFailure]). Surfaced in the console so an
    // operator can see how flaky the model's JSON was and inspect exactly what it returned.
    val jsonParseFailureCount: Int = 0,
    @Column(columnDefinition = "text")
    val jsonParseFailuresJson: String = "[]",
    val createdAt: Instant = Instant.now(),
)
