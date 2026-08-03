package org.taonity.sinairllmbot.bot.ingestion

class IngestionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class UnsafeUrlException(message: String) : RuntimeException(message)
