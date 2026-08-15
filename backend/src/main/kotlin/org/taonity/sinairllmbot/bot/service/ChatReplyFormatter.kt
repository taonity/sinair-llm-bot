package org.taonity.sinairllmbot.bot.service

internal object ChatReplyFormatter {
    private const val TRIPLE_BACKTICKS = "```"
    private const val MAX_VISIBLE_LINES = 6
    private const val APPROXIMATE_CHARS_PER_LINE = 135
    private val BLANK_LINES = Regex("\n[ \t]*\n+")

    fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .extractBlankLineDelimitedDescription()
        .removeUnsupportedBold()
        .replace(BLANK_LINES, "\n")
        .compactFenceBoundaries()

    fun wrapLongReply(text: String): String {
        val visibleLines = text.lineSequence().sumOf { line ->
            maxOf(1, (line.length + APPROXIMATE_CHARS_PER_LINE - 1) / APPROXIMATE_CHARS_PER_LINE)
        }
        if (visibleLines <= MAX_VISIBLE_LINES) return text
        val fenceCount = text.windowed(TRIPLE_BACKTICKS.length).count { it == TRIPLE_BACKTICKS }
        extractDescriptionFromWholeBlock(text)?.let { return it }
        if (fenceCount > 0 && fenceCount % 2 == 0) {
            return text
        }
        if (text.lineSequence().any { it.startsWith("> ") }) {
            return wrapTextBetweenQuotes(text)
        }
        val content = text.replace(TRIPLE_BACKTICKS, "").trim()
        return "$TRIPLE_BACKTICKS$content$TRIPLE_BACKTICKS"
    }

    private fun extractDescriptionFromWholeBlock(text: String): String? {
        if (!text.startsWith(TRIPLE_BACKTICKS) || !text.endsWith(TRIPLE_BACKTICKS)) return null
        if (text.windowed(TRIPLE_BACKTICKS.length).count { it == TRIPLE_BACKTICKS } != 2) return null

        val lines = text
            .removePrefix(TRIPLE_BACKTICKS)
            .removeSuffix(TRIPLE_BACKTICKS)
            .trim('\n')
            .lines()
        if (lines.size < 2) return null

        val description = lines.first().trim()
        val details = lines.drop(1).joinToString("\n").trim()
        if (description.isEmpty() || details.isEmpty()) return null
        return "$description$TRIPLE_BACKTICKS$details$TRIPLE_BACKTICKS"
    }

    private fun String.extractBlankLineDelimitedDescription(): String {
        if (!startsWith("$TRIPLE_BACKTICKS\n")) return this
        if (windowed(TRIPLE_BACKTICKS.length).count { it == TRIPLE_BACKTICKS } != 2) return this

        val closingFence = indexOf(TRIPLE_BACKTICKS, TRIPLE_BACKTICKS.length)
        if (closingFence < 0) return this
        val blockContent = substring(TRIPLE_BACKTICKS.length, closingFence).trim('\n')
        val delimiter = BLANK_LINES.find(blockContent) ?: return this
        val description = blockContent.substring(0, delimiter.range.first).trim()
        val details = blockContent.substring(delimiter.range.last + 1).trim()
        if (description.isEmpty() || details.isEmpty()) return this

        val conclusion = substring(closingFence + TRIPLE_BACKTICKS.length).trim()
        return buildString {
            append(description).append(TRIPLE_BACKTICKS).append(details).append(TRIPLE_BACKTICKS)
            if (conclusion.isNotEmpty()) append(conclusion)
        }
    }

    private fun String.compactFenceBoundaries(): String = split(TRIPLE_BACKTICKS)
        .joinToString(TRIPLE_BACKTICKS) { part -> part.trim('\n') }

    private fun String.removeUnsupportedBold(): String = split(TRIPLE_BACKTICKS)
        .mapIndexed { index, part -> if (index % 2 == 0) part.replace("**", "") else part }
        .joinToString(TRIPLE_BACKTICKS)

    private fun wrapTextBetweenQuotes(text: String): String = buildList {
        val textLines = mutableListOf<String>()
        fun flushText() {
            if (textLines.isEmpty()) return
            add("$TRIPLE_BACKTICKS${textLines.joinToString("\n")}$TRIPLE_BACKTICKS")
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