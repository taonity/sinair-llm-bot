package org.taonity.sinairllmbot.advisory

import java.lang.String.format

enum class Advisory(
    private val title: String,
    private val detailTemplate: String,
    private val severity: Severity
) {
    ;

    fun toDto(args: List<String> = emptyList()): AdvisoryDto {
        return AdvisoryDto(
            code = this.name,
            title = this.title,
            detail = format(detailTemplate, *args.toTypedArray()),
            severity = this.severity
        )
    }
}

enum class Severity {
    INFO,
    WARNING,
    ERROR
}
