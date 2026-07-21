package org.taonity.sinairllmbot.observability.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class ControllerLoggingInterceptor : HandlerInterceptor {

    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val START_TIME_ATTRIBUTE = "controllerLogging.startTime"
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis())

        val level = handler.getMethodAnnotation(EndpointLogLevel::class.java)?.value ?: LogLevel.INFO
        val entryLevel = level.demote()
        logAtLevel(entryLevel) { "${describe(request, handler)}" }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        if (handler !is HandlerMethod) return

        val startTime = request.getAttribute(START_TIME_ATTRIBUTE) as? Long ?: return
        val elapsed = System.currentTimeMillis() - startTime

        if (ex != null) {
            LOGGER.warn(ex) { "${describe(request, handler)} failed in ${elapsed}ms" }
            return
        }

        val level = handler.getMethodAnnotation(EndpointLogLevel::class.java)?.value ?: LogLevel.INFO
        logAtLevel(level) { "${describe(request, handler)} completed in ${elapsed}ms" }
    }

    private fun describe(request: HttpServletRequest, handler: HandlerMethod): String {
        val httpMethod = request.method
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
            ?: request.requestURI
        val className = handler.beanType.simpleName
        val methodName = handler.method.name
        return "$httpMethod $pattern -> $className.$methodName()"
    }

    private fun logAtLevel(level: LogLevel, msg: () -> String) {
        when (level) {
            LogLevel.TRACE -> LOGGER.trace(msg)
            LogLevel.DEBUG -> LOGGER.debug(msg)
            LogLevel.INFO  -> LOGGER.info(msg)
            LogLevel.WARN  -> LOGGER.warn(msg)
            LogLevel.ERROR -> LOGGER.error(msg)
        }
    }
}
