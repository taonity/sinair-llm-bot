package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import java.time.Instant

@ConfigurationProperties("app.ingestion.cache")
data class SourceCacheProperties(val ttlSeconds: Long, val maxEntries: Int, val maxEntryChars: Int)

@Component
class SourceCache(private val properties: SourceCacheProperties) {
    private data class Entry(val document: SourceDocument, val expiresAt: Instant)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun get(key: String, now: Instant = Instant.now()): SourceDocument? {
        entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
        return entries[key]?.document
    }

    @Synchronized
    fun put(key: String, document: SourceDocument, now: Instant = Instant.now()) {
        val size = document.contentText.orEmpty().length + document.imageDataUrl.orEmpty().length
        if (size > properties.maxEntryChars || properties.maxEntries <= 0 || properties.ttlSeconds <= 0) return
        entries[key] = Entry(document, now.plusSeconds(properties.ttlSeconds))
        while (entries.size > properties.maxEntries) entries.remove(entries.keys.first())
    }
}