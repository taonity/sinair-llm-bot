package org.taonity.sinairllmbot.security.config

import java.time.Duration
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
 *
 * `DefaultCookieSerializer` defaults `cookieMaxAge` to `-1`, which emits a browser-session cookie
 * (no Max-Age/Expires) that is discarded when the browser closes, forcing re-authentication long
 * before the server-side session actually expires. Aligning the cookie Max-Age with the configured
 * `server.servlet.session.timeout` makes the cookie persist for the full session lifetime.
 */
@Configuration
class HttpSessionCookieConfig(
    private val appProperties: AppProperties,
    @Value("\${server.servlet.session.cookie.name}") private val sessionCookieName: String,
    @Value("\${server.servlet.session.cookie.domain}") private val sessionCookieDomain: String,
    @Value("\${server.servlet.session.timeout:7d}") private val sessionTimeout: Duration,
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
        serializer.setCookieMaxAge(sessionTimeout.seconds.toInt())
        return serializer
    }
}
