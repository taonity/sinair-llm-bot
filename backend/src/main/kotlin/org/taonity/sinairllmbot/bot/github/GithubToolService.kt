package org.taonity.sinairllmbot.bot.github

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.taonity.sinairllmbot.bot.client.Tool
import org.taonity.sinairllmbot.bot.config.GithubSettings
import org.taonity.sinairllmbot.bot.tools.LlmToolContributor
import org.taonity.sinairllmbot.bot.tools.ToolCapability
import org.taonity.sinairllmbot.bot.tools.ToolExecutionContext
import org.taonity.sinairllmbot.bot.pipeline.PipelineContextTracker
import tools.jackson.databind.ObjectMapper

/**
 * Exposes the read-only GitHub capabilities as LLM function tools and executes the model's calls.
 *
 * Two tools back the code-awareness feature: `search_code` finds where something lives and
 * `get_file` reads a specific file (or lists a directory). Results are returned as plain text for
 * the model to ground its reply in; every call is read-only and accepts public repositories.
 */
@Service
class GithubToolService(
    private val githubCodeClient: GithubCodeClient,
    private val settings: GithubSettings,
    private val objectMapper: ObjectMapper,
    private val pipelineContextTracker: PipelineContextTracker,
) : LlmToolContributor {
    private companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    private val properties get() = settings.github()

    val repoLookupEnabled: Boolean get() = properties.repoLookup.enabled
    override val capability: ToolCapability = ToolCapability.REPOSITORY

    fun toolDefinitions(): List<Tool> = listOf(
        Tool.function(
            name = "search_code",
            description = "Search source code across public GitHub repositories. By default searches " +
                "the '${properties.org}' organization; use owner/repository for another public repo. " +
                "Returns matching repo/path locations. Use concrete symbols, function " +
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
                        "description" to "Optional repository name, or owner/repository, to restrict the search to.",
                    ),
                ),
                "required" to listOf("query"),
            ),
        ),
        Tool.function(
            name = "get_file",
            description = "Read a file, or list a directory, from a public GitHub repository. Use " +
                "owner/repository for repos outside '${properties.org}'. Read-only; defaults to the " +
                "repository's default branch.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "repo" to mapOf(
                        "type" to "string",
                        "description" to "Repository name, or owner/repository for a public repo.",
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
        Tool.function(
            name = "list_repos",
            description = "List all public repositories in the '${properties.org}' GitHub organization. " +
                "Returns repository names with descriptions. Use this to discover which repos exist " +
                "before searching code or reading files.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
            ),
        ),
    )

    override fun definitions(context: ToolExecutionContext): List<Tool> =
        if (properties.mcp.enabled) emptyList() else toolDefinitions()

    override fun supports(name: String): Boolean =
        !properties.mcp.enabled && (name == "search_code" || name == "get_file" || name == "list_repos")

    /** Dispatches a single tool call. Never throws: failures come back as an `ERROR: ...` string. */
    fun execute(name: String, argumentsJson: String): String {
        val args: Map<*, *> = runCatching { objectMapper.readValue(argumentsJson, Map::class.java) }
            .getOrNull() ?: emptyMap<String, Any?>()
        fun arg(key: String) = (args[key] as? String)?.takeIf { it.isNotBlank() }
        return when (name) {
            "search_code" -> searchCode(arg("query"), arg("repo"))
            "get_file" -> getFile(arg("repo"), arg("path"), arg("ref"))
            "list_repos" -> listRepos()
            else -> "ERROR: unknown tool '$name'"
        }
    }

    override fun execute(
        context: ToolExecutionContext,
        name: String,
        argumentsJson: String,
    ): String {
        val args: Map<*, *> = runCatching { objectMapper.readValue(argumentsJson, Map::class.java) }
            .getOrDefault(emptyMap<String, Any?>())
        if (name == "get_file") {
            val repo = (args["repo"] as? String).orEmpty()
            val path = (args["path"] as? String).orEmpty()
            if (repo.isNotBlank() && path.isNotBlank()) {
                pipelineContextTracker.recordSource("github://$repo/$path")
            }
        } else if (name == "search_code") {
            pipelineContextTracker.recordSource("github://code-search")
        } else if (name == "list_repos") {
            pipelineContextTracker.recordSource("github://org-repos")
        }
        return execute(name, argumentsJson)
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

    private fun listRepos(): String {
        return runCatching {
            val repos = githubCodeClient.listRepos()
            if (repos.isEmpty()) "No public repositories found in '${properties.org}'."
            else repos.joinToString("\n") { r ->
                buildString { append(r.name); r.description?.let { append(" — $it") } }
            }
        }.getOrElse {
            LOGGER.warn(it) { "list_repos failed" }
            "ERROR: failed to list repositories: ${it.message}"
        }
    }
}
