package org.taonity.sinairllmbot.bot.context

import org.springframework.stereotype.Component

/**
 * Defense-in-depth sanitizer for diagnostic payload excerpts. Safe config and normal DTOs are
 * allowlisted before this point; this catches credentials embedded in stored provider payloads.
 */
@Component
class ContextResultSanitizer {
    private val jsonSecret = Regex(
        """(?i)("(?:api[_-]?key|authorization|cookie|set-cookie|password|secret|access[_-]?token|refresh[_-]?token)"\s*:\s*")([^"]*)(")""",
    )
    private val bearer = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val querySecret = Regex("""(?i)([?&](?:api[_-]?key|token|key|secret)=)[^&\s"]+""")

    fun sanitize(value: String, maxChars: Int = 4_000): String {
        val redacted = value
            .replace(jsonSecret) { "${it.groupValues[1]}[REDACTED]${it.groupValues[3]}" }
            .replace(bearer, "Bearer [REDACTED]")
            .replace(querySecret) { "${it.groupValues[1]}[REDACTED]" }
        return if (redacted.length <= maxChars) redacted else redacted.take(maxChars) + "\n...[truncated]"
    }
}
