package org.taonity.sinairllmbot.advisory

data class AdvisoryResponse(
    val advisories: Set<AdvisoryDto> = setOf()
)
