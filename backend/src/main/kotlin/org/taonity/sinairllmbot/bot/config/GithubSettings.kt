package org.taonity.sinairllmbot.bot.config

/**
 * Narrow live-config seam for GitHub code-awareness. Implemented by the runtime settings provider so
 * the repo-lookup components read the current [GithubProperties] on each access (live-apply), while
 * unit tests can supply a fixed value without the full settings machinery.
 */
fun interface GithubSettings {
    fun github(): GithubProperties
}
