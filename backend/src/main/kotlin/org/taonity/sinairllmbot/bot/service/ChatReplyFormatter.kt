package org.taonity.sinairllmbot.bot.service

internal object ChatReplyFormatter {
    private const val FENCE = "```"
    private const val DEFAULT_LEAD = "Подробности:"

    fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .split(FENCE).mapIndexed { index, part ->
            if (index % 2 == 1) part.trim('\n')
            else part.replace("**", "").replace(Regex("\n[ \t]*\n+"), "\n").trim('\n')
        }.joinToString(FENCE)

    fun wrapLongReply(text: String): String {
        if (text.contains(FENCE)) {
            val balanced = if (text.split(FENCE).size % 2 == 0) "$text$FENCE" else text
            return if (balanced.startsWith(FENCE)) "$DEFAULT_LEAD$balanced" else balanced
        }
        if (visibleLines(text) <= 6) return text
        val lines = text.lines()
        if (lines.any { it.startsWith("> ") }) {
            return lines.joinToString("\n") { line ->
                if (line.startsWith("> ") || visibleLines(line) <= 6) line else "$DEFAULT_LEAD$FENCE$line$FENCE"
            }
        }
        val firstLine = lines.first()
        val sentenceEnd = Regex("[.!?:](?:\\s|$)").find(text)?.range?.last?.plus(1)
        val leadLength = when {
            lines.size > 1 && firstLine.length in 1..180 -> firstLine.length
            sentenceEnd != null && sentenceEnd in 1..180 -> sentenceEnd
            else -> 0
        }
        val lead = text.take(leadLength).trim().ifEmpty { DEFAULT_LEAD }
        val details = text.drop(leadLength).trim()
        return "$lead$FENCE$details$FENCE"
    }

    fun visibleLines(text: String): Int = text.lineSequence().sumOf { maxOf(1, (it.length + 134) / 135) }

    fun limit(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val notice = "\nЧасть ответа не поместилась в лимит чата."
        val available = (maxChars - notice.length).coerceAtLeast(0)
        val result = StringBuilder()
        for ((index, part) in text.split(FENCE).withIndex()) {
            val segment = if (index % 2 == 1) "$FENCE$part$FENCE" else part
            if (result.length + segment.length > available) {
                if (index % 2 == 0) result.append(segment.take(available - result.length).trimEnd())
                break
            }
            result.append(segment)
        }
        return (result.toString().trimEnd() + notice).take(maxChars)
    }
}