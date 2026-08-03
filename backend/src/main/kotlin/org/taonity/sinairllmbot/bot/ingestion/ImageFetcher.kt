package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionSettings
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType
import java.util.Base64

@Component
class ImageFetcher(
    private val safeHttpFetcher: SafeHttpFetcher,
    private val settings: IngestionSettings,
) {
    private val properties get() = settings.ingestion()

    fun fetch(url: String): SourceDocument {
        val result = safeHttpFetcher.fetch(
            rawUrl = url,
            requireHttps = true,
            maxBytes = properties.image.maxBytes,
            accept = "image/*",
        )

        val mimeType = ImageTypeDetector.detect(result.body)
            ?: throw IngestionException("URL did not return a recognised image: $url")

        val base64 = Base64.getEncoder().encodeToString(result.body)
        val dataUrl = "data:$mimeType;base64,$base64"

        return SourceDocument(
            sourceId = "image:${result.finalUrl}",
            sourceType = SourceType.IMAGE,
            url = url,
            canonicalUrl = result.finalUrl,
            title = url.substringAfterLast('/').substringBefore('?').ifBlank { "image" },
            imageDataUrl = dataUrl,
            metadata = mapOf(
                "mimeType" to mimeType,
                "bytes" to result.body.size.toString(),
            ),
        )
    }
}
