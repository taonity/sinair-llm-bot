package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.model.LinkKind
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType
import java.nio.charset.StandardCharsets

/**
 * Fetches a generic web page through [SafeHttpFetcher] (SSRF-guarded, size-capped) and extracts the
 * readable main content, title, canonical URL, and documentation/API links.
 */
@Component
class HtmlPageFetcher(
    private val safeHttpFetcher: SafeHttpFetcher,
    private val contentExtractor: ContentExtractor,
    private val properties: IngestionProperties,
) {
    fun fetch(url: String): SourceDocument {
        val result = safeHttpFetcher.fetch(
            rawUrl = url,
            requireHttps = false,
            maxBytes = properties.maxPageBytes,
            accept = "text/html,application/xhtml+xml",
        )

        val contentType = result.contentType?.lowercase().orEmpty()
        val looksHtml = contentType.isBlank() ||
            contentType.contains("html") ||
            contentType.contains("xml") ||
            contentType.startsWith("text/")
        if (!looksHtml) {
            throw IngestionException("Unsupported content-type '$contentType' for $url")
        }

        val html = String(result.body, StandardCharsets.UTF_8)
        val extracted = contentExtractor.extract(html, result.finalUrl)

        return SourceDocument(
            sourceId = "web:${result.finalUrl}",
            sourceType = SourceType.WEB_PAGE,
            url = url,
            canonicalUrl = extracted.canonicalUrl ?: result.finalUrl,
            title = extracted.title,
            contentText = extracted.text.ifBlank { null },
            links = extracted.links.filter {
                it.kind == LinkKind.DOCS || it.kind == LinkKind.API || it.kind == LinkKind.REPO
            },
            images = extracted.images,
        )
    }
}
