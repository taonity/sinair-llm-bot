package org.taonity.sinairllmbot.bot.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriUtils
import org.taonity.sinairllmbot.bot.config.GithubProperties
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/**
 * Read-only GitHub REST client scoped to one organization. It only issues GET requests and the
 * organization is fixed from [GithubProperties.org] (never taken from the model), so a lookup can
 * never reach a repository outside it. Backs the agentic repo-lookup tools (search_code/get_file).
 */
@Component
class GithubCodeClient(
    private val properties: GithubProperties,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val REPO_PATTERN = Regex("^[A-Za-z0-9._-]+$")
    }

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.apiBaseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
                setReadTimeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
            },
        )
        .defaultHeader("User-Agent", properties.userAgent)
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build()

    /** Searches code across the organization. An optional [repo] narrows it to one repository. */
    fun searchCode(query: String, repo: String?): List<CodeHit> {
        val scope = buildString {
            append(query.trim())
            append(" org:").append(properties.org)
            repo?.takeIf { it.isNotBlank() }
                ?.let { append(" repo:").append(properties.org).append('/').append(sanitizeRepo(it)) }
        }
        val response = restClient.get()
            .uri { builder ->
                builder.path("/search/code")
                    .queryParam("q", scope)
                    .queryParam("per_page", properties.repoLookup.maxSearchResults)
                    .build()
            }
            .headers { authorize(it) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(CodeSearchResponse::class.java)
        return response?.items?.map {
            CodeHit(repo = it.repository?.name ?: "?", path = it.path ?: "?", url = it.htmlUrl)
        }.orEmpty()
    }

    /** Reads a file (or lists a directory) on the given [ref] (default branch when null). */
    fun getFile(repo: String, path: String, ref: String?): FileContent {
        val safeRepo = sanitizeRepo(repo)
        val safePath = sanitizePath(path)
        val encodedPath = safePath.split('/').filter { it.isNotEmpty() }
            .joinToString("/") { UriUtils.encodePathSegment(it, StandardCharsets.UTF_8) }
        val body = restClient.get()
            .uri { builder ->
                builder.path("/repos/").path(properties.org).path("/").path(safeRepo)
                    .path("/contents/").path(encodedPath)
                if (!ref.isNullOrBlank()) builder.queryParam("ref", ref)
                builder.build()
            }
            .headers { authorize(it) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("empty response from GitHub contents API")

        if (body.trimStart().startsWith("[")) {
            val entries = objectMapper.readValue(body, Array<DirEntry>::class.java)
            val listing = entries.joinToString("\n") { "- ${it.name} (${it.type})" }
            return FileContent(path = safePath, isDirectory = true, text = listing)
        }

        val meta = objectMapper.readValue(body, ContentMetadata::class.java)
        val decoded = meta.content
            ?.let { runCatching { String(Base64.getMimeDecoder().decode(it), StandardCharsets.UTF_8) }.getOrDefault("") }
            .orEmpty()
        val maxChars = properties.repoLookup.maxFileChars
        return FileContent(
            path = safePath,
            isDirectory = false,
            text = decoded.take(maxChars),
            truncated = decoded.length > maxChars,
        )
    }

    private fun authorize(headers: HttpHeaders) {
        properties.token?.takeIf { it.isNotBlank() }?.let { headers.setBearerAuth(it) }
    }

    /** Rejects anything that isn't a bare repo name; tolerates a leading "org/" the model may add. */
    private fun sanitizeRepo(repo: String): String {
        val name = repo.trim().substringAfterLast('/')
        require(name.isNotBlank() && REPO_PATTERN.matches(name)) { "invalid repository name: $repo" }
        return name
    }

    /** Normalizes a repo-relative path and blocks traversal segments. */
    private fun sanitizePath(path: String): String {
        val clean = path.trim().trimStart('/')
        require(clean.isNotBlank() && clean.split('/').none { it == ".." }) { "invalid path: $path" }
        return clean
    }

    data class CodeHit(val repo: String, val path: String, val url: String?)

    data class FileContent(
        val path: String,
        val isDirectory: Boolean,
        val text: String,
        val truncated: Boolean = false,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CodeSearchResponse(val items: List<Item> = emptyList()) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Item(
            val path: String? = null,
            @JsonProperty("html_url") val htmlUrl: String? = null,
            val repository: Repository? = null,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Repository(
                val name: String? = null,
                @JsonProperty("full_name") val fullName: String? = null,
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentMetadata(
        val content: String? = null,
        val encoding: String? = null,
        val type: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DirEntry(
        val name: String? = "?",
        val type: String? = "?",
    )
}
