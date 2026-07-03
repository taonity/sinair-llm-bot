package org.taonity.sinairllmbot.bot.ingestion

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionProperties
import org.taonity.sinairllmbot.bot.ingestion.model.LinkKind
import org.taonity.sinairllmbot.bot.ingestion.model.SourceDocument
import org.taonity.sinairllmbot.bot.ingestion.model.SourceType
import java.time.Duration

/**
 * Fetches a GitHub repository's context via the REST API (not scraping): metadata, latest commit
 * SHA and the rendered README, which is then cleaned into text + links + images. Unauthenticated
 * for the MVP, so it is subject to GitHub's 60 req/hour anonymous rate limit.
 */
@Component
class GitHubReadmeFetcher(
    private val contentExtractor: ContentExtractor,
    private val properties: IngestionProperties,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val README_HTML = MediaType.parseMediaType("application/vnd.github.html")
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl("https://api.github.com")
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
                setReadTimeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
            },
        )
        .defaultHeader("User-Agent", properties.userAgent)
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build()

    fun fetch(owner: String, repo: String, originalUrl: String): SourceDocument {
        val meta = runCatching {
            restClient.get().uri("/repos/{owner}/{repo}", owner, repo)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(RepoMetadata::class.java)
        }.getOrNull() ?: throw IngestionException("GitHub repo not accessible: $owner/$repo")

        val commitSha = runCatching {
            restClient.get().uri("/repos/{owner}/{repo}/commits?per_page=1", owner, repo)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Array<Commit>::class.java)
                ?.firstOrNull()?.sha
        }.getOrNull()

        val readmeHtml = runCatching {
            restClient.get().uri("/repos/{owner}/{repo}/readme", owner, repo)
                .accept(README_HTML)
                .retrieve()
                .body(String::class.java)
        }.onFailure { LOGGER.debug(it) { "No README for $owner/$repo" } }.getOrNull()

        val extracted = readmeHtml?.let { contentExtractor.extract(it, meta.htmlUrl ?: originalUrl) }

        val content = buildString {
            meta.description?.let { append(it).append("\n\n") }
            extracted?.text?.let { append(it) }
        }.trim().ifBlank { null }

        return SourceDocument(
            sourceId = "github:${owner.lowercase()}/${repo.lowercase()}",
            sourceType = SourceType.GITHUB_README,
            url = originalUrl,
            canonicalUrl = meta.htmlUrl,
            title = meta.fullName ?: "$owner/$repo",
            contentText = content,
            links = (extracted?.links ?: emptyList())
                .filter { it.kind != LinkKind.BADGE && it.kind != LinkKind.SOCIAL },
            images = extracted?.images ?: emptyList(),
            metadata = buildMap {
                meta.defaultBranch?.let { put("defaultBranch", it) }
                commitSha?.let { put("commitSha", it) }
                meta.language?.let { put("language", it) }
                meta.stargazersCount?.let { put("stars", it.toString()) }
            },
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RepoMetadata(
        @JsonProperty("full_name") val fullName: String? = null,
        val description: String? = null,
        @JsonProperty("default_branch") val defaultBranch: String? = null,
        @JsonProperty("html_url") val htmlUrl: String? = null,
        val language: String? = null,
        @JsonProperty("stargazers_count") val stargazersCount: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Commit(
        val sha: String? = null,
    )
}
