package org.taonity.sinairllmbot.bot.ingestion.config

/**
 * Narrow live-config seam for URL/content ingestion. Implemented by the runtime settings provider
 * so ingestion components read the current [IngestionProperties] on each access (live-apply), while
 * unit tests can supply a fixed value without the full settings machinery.
 */
fun interface IngestionSettings {
    fun ingestion(): IngestionProperties
}
