package org.taonity.sinairllmbot.security.config

import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import org.taonity.sinairllmbot.common.config.AppProperties

// Spring Session ignores server.servlet.session.cookie.* and defaults to a browser-session cookie.
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
