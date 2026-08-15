package org.taonity.sinairllmbot.bot.service

internal object ChatReplyFormatter {
    private const val TRIPLE_BACKTICKS = "```"
    private const val MAX_VISIBLE_LINES = 6
    private const val APPROXIMATE_CHARS_PER_LINE = 135
    private val BLANK_LINES = Regex("\n[ \t]*\n+")

    fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .removeUnsupportedBold()
        .replace(BLANK_LINES, "\n")

    fun wrapLongReply(text: String): String {
        val visibleLines = text.lineSequence().sumOf { line ->
            maxOf(1, (line.length + APPROXIMATE_CHARS_PER_LINE - 1) / APPROXIMATE_CHARS_PER_LINE)
        }
        if (visibleLines <= MAX_VISIBLE_LINES) return text
        if (text.startsWith(TRIPLE_BACKTICKS) && text.endsWith(TRIPLE_BACKTICKS) &&
            text.windowed(TRIPLE_BACKTICKS.length).count { it == TRIPLE_BACKTICKS } == 2
        ) {
            return text
        }
        if (!text.startsWith(TRIPLE_BACKTICKS) && text.endsWith(TRIPLE_BACKTICKS) &&
            text.windowed(TRIPLE_BACKTICKS.length).count { it == TRIPLE_BACKTICKS } == 2
        ) {
            return text
        }
        if (text.lineSequence().any { it.startsWith("> ") }) {
            return wrapTextBetweenQuotes(text)
        }
        val content = text.replace(TRIPLE_BACKTICKS, "").trim()
        return "$TRIPLE_BACKTICKS\n$content\n$TRIPLE_BACKTICKS"
    }

    private fun String.removeUnsupportedBold(): String = split(TRIPLE_BACKTICKS)
        .mapIndexed { index, part -> if (index % 2 == 0) part.replace("**", "") else part }
        .joinToString(TRIPLE_BACKTICKS)

    private fun wrapTextBetweenQuotes(text: String): String = buildList {
        val textLines = mutableListOf<String>()
        fun flushText() {
            if (textLines.isEmpty()) return
            add("$TRIPLE_BACKTICKS\n${textLines.joinToString("\n")}\n$TRIPLE_BACKTICKS")
            textLines.clear()
        }

        text.replace(TRIPLE_BACKTICKS, "").lineSequence().forEach { line ->
            if (line.startsWith("> ")) {
                flushText()
                add(line)
            } else {
                textLines += line
            }
        }
        flushText()
    }.joinToString("\n")
}