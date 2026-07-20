package org.taonity.sinairllmbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Data-retention and cleanup-scheduling configuration. Each policy owns how long records are kept
 * (`retention-days`) and the cron expression that fires its cleanup job. The cron is honoured live
 * by the dynamic scheduler, so edits made in the console take effect from the next scheduling cycle.
 */
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
