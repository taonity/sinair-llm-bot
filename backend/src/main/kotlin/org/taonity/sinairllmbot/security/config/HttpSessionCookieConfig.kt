package org.taonity.sinairllmbot.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import org.taonity.sinairllmbot.common.config.AppProperties

/**
 * Spring Session does not read `server.servlet.session.cookie.*`, so without this it would emit its
 * default `SESSION` cookie with no domain/secure/same-site. This restores the configured session
 * cookie name, domain and security attributes so behaviour matches the pre-Spring-Session setup.
 */
@Configuration
class HttpSessionCookieConfig(
    private val appProperties: AppProperties,
    @Value("\${server.servlet.session.cookie.name}") private val sessionCookieName: String,
    @Value("\${server.servlet.session.cookie.domain}") private val sessionCookieDomain: String,
) {

    @Bean
    fun cookieSerializer(): CookieSerializer {
        val serializer = DefaultCookieSerializer()
        serializer.setCookieName(sessionCookieName)
        serializer.setDomainName(sessionCookieDomain)
        serializer.setUseSecureCookie(appProperties.cookie.secure)
        serializer.setSameSite(appProperties.cookie.sameSite)
        serializer.setUseHttpOnlyCookie(true)
        serializer.setCookiePath("/")
        return serializer
    }
}
