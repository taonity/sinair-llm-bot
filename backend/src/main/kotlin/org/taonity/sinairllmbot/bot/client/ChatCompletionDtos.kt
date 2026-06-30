package org.taonity.sinairllmbot.bot.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    @JsonProperty("max_tokens") val maxTokens: Int? = null,
    @JsonProperty("response_format") val responseFormat: ResponseFormat? = null,
    val plugins: List<Plugin>? = null,
)

/**
 * OpenRouter plugin descriptor. Currently only the Exa-backed "web" plugin is used, to ground
 * replies in live search results when [LlmProperties.replyWebSearch] is enabled.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Plugin(
    val id: String,
    @JsonProperty("max_results") val maxResults: Int? = null,
) {
    companion object {
        fun web(maxResults: Int) = Plugin(id = "web", maxResults = maxResults)
    }
}

data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}

data class ResponseFormat(val type: String) {
    companion object {
        val JSON_OBJECT = ResponseFormat("json_object")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Choice(
        val message: ChatMessage? = null,
        @JsonProperty("finish_reason") val finishReason: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Usage(
        @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
        @JsonProperty("completion_tokens") val completionTokens: Int = 0,
        @JsonProperty("total_tokens") val totalTokens: Int = 0,
    )
}
