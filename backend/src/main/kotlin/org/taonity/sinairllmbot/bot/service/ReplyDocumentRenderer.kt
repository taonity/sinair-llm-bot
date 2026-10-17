package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class ReplyDocumentRenderer(private val objectMapper: ObjectMapper) {
    fun render(raw: String): String {
        val text = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        val document = findReplyDocument(text)
        if (document == null) {
            if (!text.startsWith("{")) return ChatReplyFormatter.wrapLongReply(ChatReplyFormatter.normalize(raw.trim()))
            return "Не удалось оформить полный ответ."
        }
        val lead = ChatReplyFormatter.normalize(document["lead"].asString()).replace("```", "").trim()
        return buildString {
            append(lead)
            val blocks = document.path("blocks")
            if (blocks.isArray) blocks.forEach { block ->
                val content = block.path("text").asString("")
                if (content.isBlank()) return@forEach
                when (block.path("kind").asString("prose")) {
                    "quote" -> {
                        append('\n')
                        append(content.lines().joinToString("\n") { "> $it" })
                        append('\n')
                    }
                    else -> {
                        if (isEmpty() || endsWith("```") || endsWith('\n')) append("Подробности:")
                        append("```")
                        append(if (block.path("kind").asString("") == "code") content else content.replace("**", ""))
                        append("```")
                    }
                }
            }
        }.trim().let { ChatReplyFormatter.wrapLongReply(it) }
    }

    fun isStructured(raw: String): Boolean =
        findReplyDocument(raw.trim().removePrefix("```json").removeSuffix("```").trim()) != null

    private fun findReplyDocument(text: String): JsonNode? {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        var lastDocument: JsonNode? = null
        text.forEachIndexed { index, character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (character) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && start >= 0) {
                        parseReplyDocument(text.substring(start, index + 1))?.let { lastDocument = it }
                        start = -1
                    }
                }
            }
        }
        return lastDocument
    }

    private fun parseReplyDocument(text: String): JsonNode? = runCatching {
        objectMapper.readTree(text).takeIf { it.path("lead").isString && it.path("blocks").isArray }
    }.getOrNull()

    companion object {
        const val CONTRACT = "FINAL ANSWER: Return only a JSON object with shape " +
            "{\"lead\":\"short useful answer or descriptive sentence\",\"blocks\":[{\"kind\":\"prose|code|quote\",\"text\":\"details\"}]}. " +
            "For short replies put the whole reply in lead and use an empty blocks array. " +
            "For detailed replies put the direct answer in lead and details in blocks. " +
            "Do not put triple-backtick fences in any field: the chat renderer adds them. " +
            "Preserve literal code exactly in code blocks. Use short separate list lines where useful " +
            "in prose blocks; no markdown headings, bold, tables, or markdown links. " +
            "Use plain URLs and single-backtick inline highlights. Quotes are separate quote blocks. " +
            "Only place an allowed smiley in lead, never in blocks."
    }
}