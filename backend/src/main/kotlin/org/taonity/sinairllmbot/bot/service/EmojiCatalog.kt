package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class EmojiCatalog {
    val emojis: List<String> = load()

    val promptList: String = emojis.joinToString(" ")

    private fun load(): List<String> {
        return try {
            ClassPathResource(RESOURCE).inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
        } catch (exception: Exception) {
            LOGGER.warn(exception) { "Failed to load emoji catalog from $RESOURCE" }
            emptyList()
        }
    }

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val RESOURCE = "emojies.txt"
    }
}
