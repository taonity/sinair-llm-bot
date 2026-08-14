package org.taonity.sinairllmbot.bot.service

internal object ChatReplyFormatter {
    private const val TRIPLE_BACKTICKS = "```"
    private const val MAX_VISIBLE_LINES = 6
    private const val APPROXIMATE_CHARS_PER_LINE = 135

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
        val content = text.replace(TRIPLE_BACKTICKS, "").trim()
        return "$TRIPLE_BACKTICKS\n$content\n$TRIPLE_BACKTICKS"
    }
}