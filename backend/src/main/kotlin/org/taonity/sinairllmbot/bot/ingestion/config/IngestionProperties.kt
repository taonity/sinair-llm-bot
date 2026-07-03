package org.taonity.sinairllmbot.bot.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the URL-aware context ingestion feature: when a chat message contains a link,
 * the bot deterministically fetches and cleans that source before answering (see
 * `SourceIngestionService`). Sensible defaults are provided so the block is optional in yaml.
 */
@ConfigurationProperties(prefix = "app.ingestion")
data class IngestionProperties(
    val enabled: Boolean = true,
    /** Hard cap on how many URLs from a single message are fetched. */
    val maxUrlsPerMessage: Int = 3,
    val fetchTimeoutSeconds: Long = 8,
    /** Redirect hops allowed; every hop is re-validated against the SSRF rules. */
    val maxRedirects: Int = 4,
    /** Byte cap for HTML/web pages (trap protection). */
    val maxPageBytes: Long = 2_000_000,
    /** Total character budget of the grounded context handed to the model. */
    val maxContextChars: Int = 6_000,
    /** Per-source character budget within the total context. */
    val maxCharsPerSource: Int = 4_000,
    val image: Image = Image(),
    /** LLM tier (under `app.llm.tiers`) used for a reply that must analyse an attached image. */
    val visionTier: String = "vision",
    val userAgent: String = "sinair-llm-bot/1.0 (+https://github.com/)",
) {
    data class Image(
        /** Hard byte cap for a downloaded image (trap protection). */
        val maxBytes: Long = 6_000_000,
        /** URL path extensions treated as a direct, explicit image link. */
        val extensions: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"),
    )
}
