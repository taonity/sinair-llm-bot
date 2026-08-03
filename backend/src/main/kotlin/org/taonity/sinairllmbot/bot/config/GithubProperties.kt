package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.github")
data class GithubProperties(
    val org: String,
    val token: String?,
    val apiBaseUrl: String,
    val userAgent: String,
    val fetchTimeoutSeconds: Long,
    val mcp: Mcp,
    val repoLookup: RepoLookup,
) {
    data class Mcp(
        val enabled: Boolean,
        val baseUrl: String,
        val endpoint: String,
        val requestTimeoutSeconds: Long,
        val readTools: List<String>,
        val writeEnabled: Boolean,
        val writeTools: List<String>,
    )

    data class RepoLookup(
        val enabled: Boolean,
        val maxSearchResults: Int,
        val maxFileChars: Int,
    )
}
