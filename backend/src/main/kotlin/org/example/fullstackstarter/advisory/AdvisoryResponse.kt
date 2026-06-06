package org.example.fullstackstarter.advisory

data class AdvisoryResponse(
    val advisories: Set<AdvisoryDto> = setOf()
)
