package org.taonity.sinairllmbot.bot.github

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.config.GithubProperties
import tools.jackson.databind.ObjectMapper

/**
 * Exposes the read-only GitHub capabilities as LLM function tools and executes the model's calls.
 *
 * Two tools back the code-awareness feature: `search_code` finds where something lives and
 * `get_file` reads a specific file (or lists a directory). Results are returned as plain text for
 * the model to ground its reply in; every call is read-only and scoped to the configured org.
 */
@Service
class GithubToolService(
    private val githubCodeClient: GithubCodeClient,
    private val properties: GithubProperties,
    private val objectMapper: ObjectMapper,
) {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    val repoLookupEnabled: Boolean get() = properties.repoLookup.enabled
    val repoTier: String get() = properties.repoLookup.tier
    val maxRounds: Int get() = properties.repoLookup.maxRounds

    fun toolDefinitions(): List<Tool> = listOf(
        Tool.function(
            name = "search_code",
            description = "Search source code across the '${properties.org}' GitHub organization's " +
                "repositories. Returns matching repo/path locations. Use concrete symbols, function " +
                "or class names, config keys or literal strings for best results.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "Code search terms (symbols, keywords, filenames).",
                    ),
                    "repo" to mapOf(
                        "type" to "string",
                        "description" to "Optional repository name to restrict the search to.",
                    ),
                ),
                "required" to listOf("query"),
            ),
        ),
        Tool.function(
            name = "get_file",
            description = "Read a file, or list a directory, from a repository in the " +
                "'${properties.org}' organization. Read-only; defaults to the repository's default branch.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "repo" to mapOf(
                        "type" to "string",
                        "description" to "Repository name within the organization.",
                    ),
                    "path" to mapOf(
                        "type" to "string",
                        "description" to "File or directory path within the repository.",
                    ),
                    "ref" to mapOf(
                        "type" to "string",
                        "description" to "Optional branch, tag or commit SHA; defaults to the default branch.",
                    ),
                ),
                "required" to listOf("repo", "path"),
            ),
        ),
    )

    /** Dispatches a single tool call. Never throws: failures come back as an `ERROR: ...` string. */
    fun execute(name: String, argumentsJson: String): String {
        val args: Map<*, *> = runCatching { objectMapper.readValue(argumentsJson, Map::class.java) }
            .getOrNull() ?: emptyMap<String, Any?>()
        fun arg(key: String) = (args[key] as? String)?.takeIf { it.isNotBlank() }
        return when (name) {
            "search_code" -> searchCode(arg("query"), arg("repo"))
            "get_file" -> getFile(arg("repo"), arg("path"), arg("ref"))
            else -> "ERROR: unknown tool '$name'"
        }
    }

    private fun searchCode(query: String?, repo: String?): String {
        if (query == null) return "ERROR: 'query' is required."
        return runCatching {
            val hits = githubCodeClient.searchCode(query, repo)
            if (hits.isEmpty()) "No code matches for \"$query\"${repo?.let { " in $it" } ?: ""}."
            else hits.joinToString("\n") { "${it.repo}/${it.path}" }
        }.getOrElse {
            LOGGER.warn(it) { "search_code failed" }
            "ERROR: code search failed: ${it.message}"
        }
    }

    private fun getFile(repo: String?, path: String?, ref: String?): String {
        if (repo == null || path == null) return "ERROR: 'repo' and 'path' are required."
        return runCatching {
            val file = githubCodeClient.getFile(repo, path, ref)
            if (file.isDirectory) {
                "Directory $repo/${file.path}:\n${file.text}"
            } else buildString {
                append(repo).append('/').append(file.path).append(":\n")
                append(file.text.ifBlank { "(empty file)" })
                if (file.truncated) append("\n... [truncated]")
            }
        }.getOrElse {
            LOGGER.warn(it) { "get_file failed" }
            "ERROR: could not read $repo/$path: ${it.message}"
        }
    }
}
