package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Read-only GitHub access used for code awareness. The configured [org] is the default search
 * scope, while the agentic repo tools may also read public repositories outside it.
 *
 * `token` is a secret (fine-grained PAT or App installation token, `Contents`+`Metadata: read`);
 * blank means unauthenticated, which keeps working for public repos but is rate-limited and cannot
 * use code search. All values live in yaml (see the no-hardcoded-defaults convention).
 */
@ConfigurationProperties(prefix = "app.github")
data class GithubProperties(
    val org: String,
    val token: String?,
    val apiBaseUrl: String,
    val userAgent: String,
    val fetchTimeoutSeconds: Long,
    val repoLookup: RepoLookup,
) {
    data class RepoLookup(
        val enabled: Boolean,
        val tier: String,
        val maxRounds: Int,
        val maxSearchResults: Int,
        val maxFileChars: Int,
    )
}
