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

/**
 * A chat message whose [content] is either a plain [String] (the common case) or a [List] of
 * [ContentPart]s for multimodal input (text + images). Both serialize correctly for the
 * OpenAI/OpenRouter chat-completions API; responses always come back as a string.
 *
 * On responses, [annotations] carries the web-search `url_citation`s the model grounded on (null on
 * requests — omitted via NON_NULL so it never pollutes an outgoing message).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    val role: String,
    val content: Any,
    val annotations: List<Annotation>? = null,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
        fun assistant(content: String) = ChatMessage("assistant", content)

        /** Multimodal user message (e.g. grounding text plus one or more images). */
        fun userParts(parts: List<ContentPart>) = ChatMessage("user", parts)
    }
}

/**
 * A response-side annotation. Web search returns `type="url_citation"` entries pointing at the live
 * pages the model actually consulted, letting us log whether/what it searched.
 */
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

/**
 * One part of a multimodal message: either `{"type":"text","text":...}` or
 * `{"type":"image_url","image_url":{"url":...}}`. Images are passed as base64 `data:` URLs.
 */
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
