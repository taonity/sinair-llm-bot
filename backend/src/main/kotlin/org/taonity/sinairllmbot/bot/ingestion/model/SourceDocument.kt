package org.taonity.sinairllmbot.bot.ingestion.model

import java.time.Instant

/**
 * Common, source-agnostic representation of a fetched-and-cleaned resource. New source types
 * (PDF, markdown gist, etc.) can be added later by producing one of these without touching the
 * reply pipeline. Kept deliberately slim for the MVP (no caching / content hashing yet).
 */
data class SourceDocument(
    val sourceId: String,
    val sourceType: SourceType,
    val url: String,
    val canonicalUrl: String? = null,
    val title: String? = null,
    /** Cleaned, readable text (README/page body). Null for pure-image sources. */
    val contentText: String? = null,
    val links: List<SourceLink> = emptyList(),
    val images: List<SourceImage> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    /**
     * For [SourceType.IMAGE] sources only: a self-contained `data:<mime>;base64,...` URL handed to a
     * vision model. Bytes are validated and size-capped at fetch time; never a remote URL.
     */
    val imageDataUrl: String? = null,
    val fetchedAt: Instant = Instant.now(),
)

enum class SourceType(val wireName: String) {
    GITHUB_README("github_readme"),
    WEB_PAGE("web_page"),
    IMAGE("image"),
    UNKNOWN("unknown"),
}

data class SourceLink(
    val url: String,
    val text: String? = null,
    val kind: LinkKind = LinkKind.UNKNOWN,
)

/** Rough intent of a link so noisy badges/social links can be dropped and docs links prioritised. */
enum class LinkKind {
    DOCS,
    API,
    EXAMPLE,
    REPO,
    SOCIAL,
    BADGE,
    UNKNOWN,
}

data class SourceImage(
    val url: String,
    val alt: String? = null,
    /** The page/README the image was found on. */
    val sourceUrl: String,
)
