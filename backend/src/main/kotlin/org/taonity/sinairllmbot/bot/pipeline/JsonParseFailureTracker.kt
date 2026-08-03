package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component

data class JsonParseFailure(
    val label: String,
    val attempt: Int,
    val payload: String,
)

@Component
class JsonParseFailureTracker {
    private val failures = ThreadLocal<MutableList<JsonParseFailure>?>()

    fun begin() {
        failures.set(mutableListOf())
    }

    fun record(failure: JsonParseFailure) {
        failures.get()?.add(failure)
    }

    fun drain(): List<JsonParseFailure> {
        val recorded = failures.get().orEmpty().toList()
        failures.remove()
        return recorded
    }
}
