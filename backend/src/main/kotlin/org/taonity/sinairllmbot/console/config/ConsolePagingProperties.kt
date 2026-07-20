package org.taonity.sinairllmbot.console.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Console pagination limits. Separate from [org.taonity.sinairllmbot.common.config.ConsoleProperties]
 * (owner/admin emails) so that only this page-size bound is exposed as a runtime-tunable field.
 */
@ConfigurationProperties(prefix = "app.console")
data class ConsolePagingProperties(
    val maxPageSize: Int,
)
