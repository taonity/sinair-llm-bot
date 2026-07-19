package org.taonity.sinairllmbot.bot.pipeline

/**
 * Generic, extendable trace of a bot processing pipeline.
 *
 * A pipeline is just a named ([pipelineKey]) ordered sequence of [PipelineStage]s. Each decision
 * point in the pipeline records one stage with its outcome, a few displayable [PipelineField]s and,
 * where a stage produced several competing outputs, the [PipelineAlternative]s it chose between.
 * Nothing here is coupled to a specific pipeline, so a new pipeline only needs to emit its own
 * stages — the storage layer and the console render them the same way.
 */
data class PipelineStage(
    val key: String,
    val label: String,
    val status: PipelineStageStatus,
    val summary: String = "",
    val fields: List<PipelineField> = emptyList(),
    val alternatives: List<PipelineAlternative> = emptyList(),
)

/** A small labelled value shown for a stage (e.g. a triage flag or a critic score). */
data class PipelineField(
    val label: String,
    val value: String,
)

/**
 * One competing output a stage chose between (e.g. a reply candidate). [chosen] marks the one the
 * pipeline kept; [fields] carries any per-alternative metadata (e.g. critic scores).
 */
data class PipelineAlternative(
    val text: String,
    val chosen: Boolean = false,
    val fields: List<PipelineField> = emptyList(),
)

/** Coarse status of a stage, mapped to a colour/indicator in the console. */
enum class PipelineStageStatus {
    /** The stage ran and produced its normal result. */
    OK,

    /** The stage stopped the pipeline (e.g. a mute command, cooldown, "do not respond"). */
    STOP,

    /** The stage was skipped (a preceding decision made it irrelevant). */
    SKIP,

    /** The stage ran and let the pipeline continue (e.g. no command detected). */
    PASS,

    /** Purely informational stage with no gating effect. */
    INFO,
}

/**
 * Final result of a pipeline run. Kept as loose string constants (not an enum) so future pipelines
 * can introduce their own outcomes without touching this file.
 */
object PipelineOutcome {
    const val REPLIED = "REPLIED"
    const val SILENT = "SILENT"
    const val MUTED = "MUTED"
    const val COOLDOWN = "COOLDOWN"
    const val MUTE_COMMAND = "MUTE_COMMAND"
    const val UNMUTE_COMMAND = "UNMUTE_COMMAND"
    const val SUMMARY_REFRESHED = "SUMMARY_REFRESHED"
    const val SUMMARY_FAILED = "SUMMARY_FAILED"
}

/** Known pipeline identifiers. New pipelines just add a constant here. */
object PipelineKeys {
    const val REPLY = "reply"
    const val SUMMARY = "summary"
}
