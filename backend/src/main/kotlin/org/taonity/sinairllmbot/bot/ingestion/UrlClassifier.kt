package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import java.net.URI

/**
 * Deterministically routes a URL to the right fetcher: a `github.com/{owner}/{repo}` link, a direct
 * image link (by path extension), or a generic web page.
 */
@Component
class UrlClassifier(
    private val properties: IngestionProperties,
) {
    private companion object {
        private val GITHUB_REPO = Regex(
            """^https?://(?:www\.)?github\.com/([^/\s]+)/([^/\s#?]+)""",
            RegexOption.IGNORE_CASE,
        )

        /** github.com paths that are site pages, not `{owner}/{repo}` repositories. */
        private val GITHUB_RESERVED = setOf(
            "features", "about", "pricing", "marketplace", "sponsors", "topics", "collections",
            "trending", "login", "join", "settings", "notifications", "explore", "orgs", "apps",
            "customer-stories", "readme", "search", "new", "issues", "pulls", "codespaces",
        )
    }

    fun classify(url: String): ClassifiedUrl {
        GITHUB_REPO.find(url)?.let { match ->
            val owner = match.groupValues[1]
            val repo = match.groupValues[2].removeSuffix(".git")
            if (owner.lowercase() !in GITHUB_RESERVED && repo.isNotBlank()) {
                return ClassifiedUrl.GitHub(url, owner, repo)
            }
        }

        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension in properties.image.extensions) {
            return ClassifiedUrl.Image(url)
        }
        return ClassifiedUrl.Web(url)
    }
}

sealed interface ClassifiedUrl {
    val url: String

    data class GitHub(override val url: String, val owner: String, val repo: String) : ClassifiedUrl
    data class Web(override val url: String) : ClassifiedUrl
    data class Image(override val url: String) : ClassifiedUrl
}
