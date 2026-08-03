package org.taonity.sinairllmbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.retention")
data class RetentionProperties(
    val chat: Policy,
    val audit: Policy,
) {
    data class Policy(
        val retentionDays: Long,
        val cron: String,
    )
}
