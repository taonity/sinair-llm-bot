package org.taonity.sinairllmbot.bot.ingestion.config

fun interface IngestionSettings {
    fun ingestion(): IngestionProperties
}
