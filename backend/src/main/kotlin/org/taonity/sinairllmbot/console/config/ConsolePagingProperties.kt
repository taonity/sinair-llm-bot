package org.taonity.sinairllmbot.console.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.console")
data class ConsolePagingProperties(
    val maxPageSize: Int,
)
