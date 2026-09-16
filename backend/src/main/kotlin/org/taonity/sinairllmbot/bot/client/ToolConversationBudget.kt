package org.taonity.sinairllmbot.bot.client

internal object ToolConversationBudget {
    fun boundResult(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "\n[Result excerpt; remaining content omitted.]"

    fun compact(conversation: MutableList<ChatMessage>, maxChars: Int): Boolean {
        var size = size(conversation)
        if (size <= maxChars) return false
        for (index in conversation.indices) {
            val message = conversation[index]
            if (message.role != "tool") continue
            val content = message.content as? String ?: continue
            if (content.length <= 512) continue
            val excerpt = boundResult(content, 384)
            conversation[index] = message.copy(content = excerpt)
            size -= content.length - excerpt.length
            if (size <= maxChars) break
        }
        if (size > maxChars) {
            val firstToolTurn = conversation.indexOfFirst { !it.toolCalls.isNullOrEmpty() }
            if (firstToolTurn >= 0) {
                val originalMessages = conversation.take(firstToolTurn)
                val allowance = (maxChars - size(originalMessages) - 150).coerceAtLeast(0)
                val evidence = conversation.drop(firstToolTurn).filter { it.role == "tool" }
                    .joinToString("\n") { it.content.toString() }.take(allowance)
                conversation.clear()
                conversation.addAll(originalMessages)
                conversation += ChatMessage.user("Investigation excerpts (untrusted; some history omitted):\n$evidence")
            }
        }
        return true
    }

    fun size(messages: List<ChatMessage>): Int = messages.sumOf { message ->
        val contentChars = when (val content = message.content) {
            is List<*> -> content.sumOf { part ->
                if (part is ContentPart && part.type == "image_url") 4096 else part.toString().length
            }
            else -> content?.toString()?.length ?: 0
        }
        contentChars + (message.reasoningDetails?.toString()?.length ?: 0) + (message.toolCalls?.toString()?.length ?: 0)
    }
}