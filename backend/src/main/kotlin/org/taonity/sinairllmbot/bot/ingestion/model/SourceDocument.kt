package org.taonity.sinairllmbot.bot.ingestion.model

import java.time.Instant

data class SourceDocument(
    val sourceId: String,
    val sourceType: SourceType,
    val url: String,
    val canonicalUrl: String? = null,
    val title: String? = null,
    val contentText: String? = null,
    val links: List<SourceLink> = emptyList(),
    val images: List<SourceImage> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
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
    val sourceUrl: String,
)
