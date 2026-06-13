package org.taonity.sinairllmbot.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class MdcFilter : OncePerRequestFilter() {

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        const val MDC_CORRELATION_ID = "correlationId"
        const val MDC_METHOD = "method"
        const val MDC_URI = "uri"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val correlationId = request.getHeader(CORRELATION_ID_HEADER)
                ?: UUID.randomUUID().toString()

            MDC.put(MDC_CORRELATION_ID, correlationId)
            MDC.put(MDC_METHOD, request.method)
            MDC.put(MDC_URI, request.requestURI)

            response.setHeader(CORRELATION_ID_HEADER, correlationId)

            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
