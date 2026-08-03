package org.taonity.sinairllmbot.bot.pipeline

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.Collections

@Component
class PipelineContextTracker(
    private val objectMapper: ObjectMapper,
) {
    private data class State(
        val configRevisionId: String?,
        val sources: MutableSet<String>,
    )

    private val state = ThreadLocal<State?>()

    fun begin(configRevisionId: String?) {
        state.set(State(configRevisionId, Collections.synchronizedSet(linkedSetOf())))
    }

    fun configRevisionId(): String? = state.get()?.configRevisionId

    fun recordSource(uri: String) {
        if (uri.isNotBlank()) state.get()?.sources?.add(uri.take(500))
    }

    fun drain(): PipelineContextManifest {
        val current = state.get()
        state.remove()
        return PipelineContextManifest(
            configRevisionId = current?.configRevisionId,
            sources = current?.sources?.toList().orEmpty(),
        )
    }

    fun discard() {
        state.remove()
    }

    fun serialize(manifest: PipelineContextManifest): String =
        objectMapper.writeValueAsString(manifest)
}

data class PipelineContextManifest(
    val configRevisionId: String? = null,
    val sources: List<String> = emptyList(),
)
