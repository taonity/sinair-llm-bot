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

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Tool(
    val type: String,
    val function: FunctionDef? = null,
) {
    companion object {
        fun webSearch() = Tool(type = "openrouter:web_search")

        fun function(name: String, description: String, parameters: Map<String, Any?>) =
            Tool(type = "function", function = FunctionDef(name, description, parameters))
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: Map<String, Any?>,
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FunctionCall(
        val name: String? = null,
        val arguments: String? = null,
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    val role: String,
    val content: Any? = null,
    val annotations: List<Annotation>? = null,
    @JsonProperty("tool_calls") val toolCalls: List<ToolCall>? = null,
    @JsonProperty("tool_call_id") val toolCallId: String? = null,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
        fun assistant(content: String) = ChatMessage("assistant", content)

        fun userParts(parts: List<ContentPart>) = ChatMessage("user", parts)

        fun tool(toolCallId: String, content: String) =
            ChatMessage(role = "tool", content = content, toolCallId = toolCallId)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Annotation(
    val type: String? = null,
    @JsonProperty("url_citation") val urlCitation: UrlCitation? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UrlCitation(
        val url: String? = null,
        val title: String? = null,
    )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ContentPart(
    val type: String,
    val text: String? = null,
    @JsonProperty("image_url") val imageUrl: ImageUrl? = null,
) {
    data class ImageUrl(val url: String)

    companion object {
        fun text(text: String) = ContentPart(type = "text", text = text)
        fun imageUrl(url: String) = ContentPart(type = "image_url", imageUrl = ImageUrl(url))
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
    val error: ProviderError? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Choice(
        val message: ChatMessage? = null,
        @JsonProperty("finish_reason") val finishReason: String? = null,
        @JsonProperty("native_finish_reason") val nativeFinishReason: String? = null,
        val error: ProviderError? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProviderError(
        val code: Int? = null,
        val message: String? = null,
    ) {
        fun shouldRetry(enabled: Boolean): Boolean =
            enabled && (code == 429 || code != null && code in 500..599)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Usage(
        @JsonProperty("prompt_tokens") val promptTokens: Int = 0,
        @JsonProperty("completion_tokens") val completionTokens: Int = 0,
        @JsonProperty("total_tokens") val totalTokens: Int = 0,
    )
}
