package org.taonity.sinairllmbot.bot.service

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ReplyDocumentRenderer(private val objectMapper: ObjectMapper) {
    fun render(raw: String): String {
        val text = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        if (!text.startsWith("{")) return ChatReplyFormatter.wrapLongReply(ChatReplyFormatter.normalize(raw.trim()))
        val document = runCatching { objectMapper.readTree(text) }.getOrNull()
        if (document == null || !document.path("lead").isString) return "Не удалось оформить полный ответ."
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

    fun isStructured(raw: String): Boolean = runCatching {
        val document = objectMapper.readTree(raw.trim().removePrefix("```json").removeSuffix("```").trim())
        document.path("lead").isString && document.path("blocks").isArray
    }.getOrDefault(false)

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