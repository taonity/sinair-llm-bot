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
    val tools: List<Tool>? = null,
)

/**
 * OpenRouter tool descriptor. Currently only the built-in web-search server tool is used: offering
 * it lets the model decide whether to fetch live, cited web results when grounding a reply (see
 * [LlmProperties.replyWebSearch]). It replaces the deprecated `web` plugin / `:online` model suffix.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Tool(
    val type: String,
) {
    companion object {
        fun webSearch() = Tool(type = "openrouter:web_search")
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
