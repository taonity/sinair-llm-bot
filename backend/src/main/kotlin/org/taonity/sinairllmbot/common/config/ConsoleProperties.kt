package org.taonity.sinairllmbot.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.console")
data class ConsoleProperties(
    val ownerEmails: List<String> = emptyList(),
    val adminEmails: List<String> = emptyList(),
) {
    fun isOwnerEmail(email: String): Boolean =
        ownerEmails.any { it.trim().equals(email.trim(), ignoreCase = true) }

    fun isAdminEmail(email: String): Boolean =
        adminEmails.any { it.trim().equals(email.trim(), ignoreCase = true) }
}
