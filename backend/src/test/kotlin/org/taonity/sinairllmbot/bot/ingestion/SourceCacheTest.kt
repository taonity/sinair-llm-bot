package org.taonity.sinairllmbot.bot.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType
import java.time.Instant

class SourceCacheTest {
    @Test
    fun `cache expires bounds entries and skips oversized images`() {
        val cache = SourceCache(SourceCacheProperties(60, 1, 100))
        val document = SourceDocument("id", SourceType.WEB_PAGE, "https://example.test", contentText = "evidence")
        cache.put("first", document, Instant.EPOCH)
        assertThat(cache.get("first", Instant.EPOCH.plusSeconds(59))).isEqualTo(document)
        assertThat(cache.get("first", Instant.EPOCH.plusSeconds(60))).isNull()
        cache.put("first", document, Instant.EPOCH)
        cache.put("second", document, Instant.EPOCH)
        assertThat(cache.get("first", Instant.EPOCH)).isNull()
        cache.put("large", document.copy(imageDataUrl = "a".repeat(101)), Instant.EPOCH)
        assertThat(cache.get("large", Instant.EPOCH)).isNull()
    }
}