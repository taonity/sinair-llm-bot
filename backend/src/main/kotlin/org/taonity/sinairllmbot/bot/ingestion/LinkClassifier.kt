package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.model.LinkKind

/**
 * Best-effort intent classification for links found in a README/page, so noisy badges and social
 * links can be dropped and documentation links surfaced first (per the ingestion design).
 */
@Component
class LinkClassifier {
    private companion object {
        private val BADGE_MARKERS = listOf(
            "shields.io", "badge.fury.io", "travis-ci.org", "travis-ci.com", "circleci.com",
            "codecov.io", "coveralls.io", "app.codecov.io", "badgen.net", "/badge", "badge.",
        )
        private val SOCIAL_MARKERS = listOf(
            "twitter.com", "x.com", "discord.gg", "discord.com", "t.me", "telegram.me",
            "linkedin.com", "facebook.com", "youtube.com", "youtu.be", "reddit.com", "mastodon",
            "github.com/sponsors", "patreon.com", "opencollective.com",
        )
        private val DOCS_KEYWORDS = listOf(
            "docs", "documentation", "quickstart", "quick-start", "getting-started", "guide",
            "usage", "example", "examples", "tutorial", "architecture", "wiki", "manual", "howto",
        )
        private val API_KEYWORDS = listOf("api", "reference", "swagger", "openapi")
        private val REPO_TAIL = Regex("""github\.com/[^/]+/[^/]+/?$""", RegexOption.IGNORE_CASE)
    }

    fun classify(url: String, text: String?): LinkKind {
        val lower = url.lowercase()
        if (lower.endsWith(".svg") || BADGE_MARKERS.any { lower.contains(it) }) return LinkKind.BADGE
        if (SOCIAL_MARKERS.any { lower.contains(it) }) return LinkKind.SOCIAL

        val haystack = lower + " " + (text?.lowercase().orEmpty())
        if (DOCS_KEYWORDS.any { haystack.contains(it) }) return LinkKind.DOCS
        if (API_KEYWORDS.any { haystack.contains(it) }) return LinkKind.API
        if (REPO_TAIL.containsMatchIn(lower)) return LinkKind.REPO
        return LinkKind.UNKNOWN
    }
}
