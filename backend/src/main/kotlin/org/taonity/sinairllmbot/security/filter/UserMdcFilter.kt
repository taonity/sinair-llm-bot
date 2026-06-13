package org.taonity.sinairllmbot.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.taonity.sinairllmbot.security.principal.GoogleUserPrincipal
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class UserMdcFilter : OncePerRequestFilter() {

    companion object {
        const val MDC_USER_ID = "userId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                val principal = authentication.principal
                if (principal is GoogleUserPrincipal) {
                    MDC.put(MDC_USER_ID, principal.getGoogleId())
                }
            }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_USER_ID)
        }
    }
}
