package org.example.fullstackstarter.observability.logging

import tools.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.fullstackstarter.common.config.AppProperties
import org.springframework.stereotype.Service
import org.springframework.web.util.ContentCachingRequestWrapper
import java.nio.charset.Charset
import java.util.AbstractMap
import java.util.Enumeration

@Service
class HttpServletLoggingService(
    private val appProperties: AppProperties,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val headerLoggingBlocklist = listOf(
            "host",
            "user-agent",
            "accept",
            "accept-language",
            "accept-encoding",
            "connection",
            "sec-fetch-dest",
            "sec-fetch-mode",
            "sec-fetch-site",
            "priority",
            "cookie",
            "upgrade-insecure-requests",
            "sec-fetch-user",
            "referer",
            "x-requested-with",
            "sec-ch-ua-platform",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "origin"
        )
        private val cookiesLoggingBlocklist = emptyList<String>()
        private val endpointBlocklist = listOf(
            "/actuator/health"
        )
    }

    @PostConstruct
    private fun logMinimisedLoggingModeIfEnabled() {
        if (appProperties.minimisedHttpServletLogging) {
            LOGGER.info { "Minimised logging mode enabled - app.minimised-http-servlet-logging=true" }
            LOGGER.info { "Following endpoints will be ignored: $endpointBlocklist" }
            LOGGER.info { "Following headers will be ignored: $headerLoggingBlocklist" }
            LOGGER.info { "Following cookies will be ignored: $cookiesLoggingBlocklist" }
        }
    }

    fun logRequestWithWrapping(request: HttpServletRequest): ContentCachingRequestWrapper {

        val wrappedRequest = ContentCachingRequestWrapper(request, 256 * 1024)

        if (shouldSkipEndpointLogging(request)) {
            return wrappedRequest
        }

        val requestBody = String(
            wrappedRequest.contentAsByteArray,
            request.characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8
        )

        val headersJson = getInterestedHeaders(request)
        val cookiesJson = getInterestedCookies(request)

        LOGGER.debug(
            "[{}] {} with headers {}, cookies {}, body [{}]",
            request.method,
            request.requestURI,
            headersJson,
            cookiesJson,
            requestBody
        )
        return wrappedRequest
    }

    private fun shouldSkipEndpointLogging(request: HttpServletRequest) =
        appProperties.minimisedHttpServletLogging && request.requestURI in endpointBlocklist

    private fun getInterestedCookies(request: HttpServletRequest): String = objectMapper.writeValueAsString(
        request.cookies
            .orEmpty()
            .asSequence()
            .filter(::filterCookieIfEnabled)
            .map { cookie -> AbstractMap.SimpleEntry(cookie.name, cookie.value) }
            .toList()
    )

    private fun filterCookieIfEnabled(cookie: Cookie) =
        cookie.name !in cookiesLoggingBlocklist || !appProperties.minimisedHttpServletLogging

    private fun getInterestedHeaders(request: HttpServletRequest): String = objectMapper.writeValueAsString(
        request.headerNames
            .asSequence()
            .filter(::filterHeaderIfEnabled)
            .map { headerName -> AbstractMap.SimpleEntry(headerName, request.getHeader(headerName)) }
            .toList()
    )

    private fun filterHeaderIfEnabled(headerName: String) =
        headerName !in headerLoggingBlocklist || !appProperties.minimisedHttpServletLogging

    private fun <T> Enumeration<T>.asSequence(): Sequence<T> = sequence {
        while (hasMoreElements()) {
            yield(nextElement())
        }
    }
}
