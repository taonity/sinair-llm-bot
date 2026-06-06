package org.example.fullstackstarter.advisory

import java.lang.String.format

enum class Advisory(
    private val title: String,
    private val detailTemplate: String,
    private val severity: Severity
) {
    // Add project-specific advisories here, e.g.:
    // EXTERNAL_SERVICE_PROBLEM(
    //     "Problems with external service",
    //     "Sorry! We have some problems with an external service. The fix is on the way.",
    //     Severity.ERROR
    // ),
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
