package org.taonity.sinairllmbot.bot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * The closed set of text smilies the bot is allowed to use, loaded once from `emojies.txt` on the
 * classpath. Blank lines and `#` comments are ignored. Injected into the reply prompt so the model
 * can only reach for these codes (and is told to use them sparingly).
 */
@Component
class EmojiCatalog {
    val emojis: List<String> = load()

    /** Space-separated allow-list for embedding in the prompt (empty when the file has no entries). */
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
