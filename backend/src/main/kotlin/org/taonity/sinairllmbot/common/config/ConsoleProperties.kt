package org.taonity.sinairllmbot.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the data console feature.
 *
 * Emails listed in [ownerEmails] are bootstrapped as the console OWNER on login; emails in
 * [adminEmails] are bootstrapped as console ADMINs. Matching is case-insensitive. All other users
 * start with no access and must request it.
 */
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
