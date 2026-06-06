package org.example.fullstackstarter.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.fullstackstarter.observability.logging.HttpServletLoggingService
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class AllRequestsLoggingFilter(
    private val httpServletLoggingService: HttpServletLoggingService
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val contentCachingRequestWrapper = httpServletLoggingService.logRequestWithWrapping(request)
        filterChain.doFilter(contentCachingRequestWrapper, response)
    }
}
