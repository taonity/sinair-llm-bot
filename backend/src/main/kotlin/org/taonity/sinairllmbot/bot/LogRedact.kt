package org.taonity.sinairllmbot.bot

import java.security.MessageDigest

// Stable hashes correlate URLs across logs without exposing conversation content.
object LogRedact {
    fun urlToken(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val hex = digest.take(4).joinToString("") { "%02x".format(it) }
        return "url#$hex"
    }
}
