package org.taonity.sinairllmbot.bot.pipeline

data class PipelineStage(
    val key: String,
    val label: String,
    val status: PipelineStageStatus,
    val summary: String = "",
    val fields: List<PipelineField> = emptyList(),
    val alternatives: List<PipelineAlternative> = emptyList(),
)

data class PipelineField(
    val label: String,
    val value: String,
)

data class PipelineAlternative(
    val text: String,
    val chosen: Boolean = false,
    val fields: List<PipelineField> = emptyList(),
)

enum class PipelineStageStatus {
    OK,

    STOP,

    SKIP,

    PASS,

    INFO,
}

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

object PipelineKeys {
    const val REPLY = "reply"
    const val SUMMARY = "summary"
}
