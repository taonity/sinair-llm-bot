package org.taonity.sinairllmbot.bot.ingestion

/** Recoverable failure while fetching/extracting a source; the pipeline just skips that URL. */
class IngestionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** A URL that fails SSRF / scheme validation and must never be fetched. */
class UnsafeUrlException(message: String) : RuntimeException(message)
