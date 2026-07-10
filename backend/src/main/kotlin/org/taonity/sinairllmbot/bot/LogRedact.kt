package org.taonity.sinairllmbot.bot

import java.security.MessageDigest

/**
 * Keeps logs free of conversation content while staying debuggable.
 *
 * Message text, summaries, search results and the URLs users share all reveal the topic of
 * conversation, so none of them are ever logged. When a link needs to be traceable across log lines
 * (e.g. ingested → re-fetched → failed), we log a stable, non-reversible short token instead of the
 * URL: the same link always maps to the same token, so occurrences can be correlated without
 * disclosing what the link is.
 */
object LogRedact {
    /** Stable, non-reversible short token for a URL or other identifier, e.g. "url#a1b2c3d4". */
    fun urlToken(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val hex = digest.take(4).joinToString("") { "%02x".format(it) }
        return "url#$hex"
    }
}
