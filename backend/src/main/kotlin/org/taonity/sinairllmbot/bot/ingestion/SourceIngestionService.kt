package org.taonity.sinairllmbot.bot.ingestion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.LogRedact
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument

/**
 * Entry point of the ingestion layer: given raw chat text, extracts URLs, fetches each one and
 * returns normalized [SourceDocument]s. Failures per-URL are swallowed (logged) so one bad link
 * never blocks a reply. No caching in this MVP — every message re-fetches.
 */
@Service
class SourceIngestionService(
    private val urlExtractor: UrlExtractor,
    private val sourceFetcher: SourceFetcher,
    private val properties: IngestionProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun ingestFrom(text: String): List<SourceDocument> {
        if (!properties.enabled) return emptyList()

        val urls = urlExtractor.extract(text).take(properties.maxUrlsPerMessage)
        if (urls.isEmpty()) return emptyList()

        return urls.mapNotNull { url ->
            runCatching { sourceFetcher.fetch(url) }
                .onSuccess { LOGGER.info { "Ingested ${it.sourceType.wireName} source [${LogRedact.urlToken(url)}]" } }
                .onFailure { LOGGER.info { "Ingestion skipped [${LogRedact.urlToken(url)}]: ${it.javaClass.simpleName}" } }
                .getOrNull()
        }
    }
}
