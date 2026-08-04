package org.taonity.sinairllmbot.bot.grafana

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.grafana.mcp")
data class GrafanaMcpProperties(
    val enabled: Boolean,
    val baseUrl: String,
    val endpoint: String,
    val requestTimeoutSeconds: Long,
    val datasourceUid: String,
    val containerPrefix: String,
    val services: List<String>,
    val maxResults: Int,
    val maxResultChars: Int,
)