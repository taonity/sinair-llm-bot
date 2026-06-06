package org.example.fullstackstarter.advisory

import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope

@Component
@RequestScope
class ResponseAttachments {
    private val notices: MutableMap<Advisory, List<String>> = linkedMapOf()

    val advisories: MutableSet<Advisory> = object : AbstractMutableSet<Advisory>() {
        override val size: Int get() = notices.size
        override fun iterator() = notices.keys.iterator()
        override fun add(element: Advisory): Boolean {
            if (notices.containsKey(element)) return false
            notices[element] = emptyList()
            return true
        }
    }

    fun add(advisory: Advisory, vararg args: String) {
        notices[advisory] = args.toList()
    }

    fun advisoryDtos(): Set<AdvisoryDto> =
        notices.entries.map { (advisory, args) -> advisory.toDto(args) }.toSet()
}
