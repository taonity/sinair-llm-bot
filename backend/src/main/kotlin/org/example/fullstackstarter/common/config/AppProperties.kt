package org.example.fullstackstarter.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val minimisedHttpServletLogging: Boolean,
    val csrfCookieName: String,
    val defaultSuccessUrl: String,
    val loginUrl: String,
    val cookie: Cookie
) {
    data class Cookie(
        val secure: Boolean,
        val sameSite: String
    )
}
