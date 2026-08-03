package org.taonity.sinairllmbot.bot.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.ingestion")
data class IngestionProperties(
    val enabled: Boolean = true,
    val maxUrlsPerMessage: Int = 3,
    val fetchTimeoutSeconds: Long = 8,
    val maxRedirects: Int = 4,
    val maxPageBytes: Long = 2_000_000,
    val maxContextChars: Int = 6_000,
    val maxCharsPerSource: Int = 4_000,
    val maxDocLinks: Int = 5,
    val image: Image = Image(),
    val visionTier: String = "vision",
    val userAgent: String = "sinair-llm-bot/1.0 (+https://github.com/)",
) {
    data class Image(
        val maxBytes: Long = 6_000_000,
        val extensions: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"),
    )
}
