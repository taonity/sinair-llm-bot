package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component

@Component
class UrlExtractor {
    private companion object {
        private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)
        private val TRAILING = charArrayOf('.', ',', ')', ']', '!', '?', ';', ':', '»', '"', '\'')
    }

    fun extract(text: String): List<String> =
        URL_REGEX.findAll(text)
            .map { it.value.trimEnd(*TRAILING) }
            .filter { it.length > "https://".length }
            .distinct()
            .toList()
}
