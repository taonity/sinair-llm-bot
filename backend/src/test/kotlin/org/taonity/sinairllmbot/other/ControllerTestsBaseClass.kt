package org.taonity.sinairllmbot.other

import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2", "stub-google")
class ControllerTestsBaseClass {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Value("\${server.servlet.session.cookie.name}")
    lateinit var sessionCookieName: String

    // Drives the stub OAuth2 flow and returns the authenticated session cookie. State is carried
    // between the authorization request and the callback via the Spring Session cookie (Spring
    // Session is DB-backed, so MockHttpSession is not used for session storage).
    fun authorizeOAuth2(registrationId: String = "google-sinair-llm-bot"): Cookie {
        val authResult = mockMvc.perform(
            get("/oauth2/authorization/$registrationId")
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()
        val state = getState(authResult)
        val authRequestCookie = extractSessionCookie(authResult)
            ?: error("No '$sessionCookieName' session cookie set on the authorization request response")

        val callbackResult = mockMvc.perform(
            get("/login/oauth2/code/$registrationId")
                .cookie(authRequestCookie)
                .param("code", "stub-auth-code")
                .param("state", state)
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()

        // The session id is rotated on successful login (session-fixation protection), so prefer
        // the cookie from the callback response; fall back to the pre-login one if not re-issued.
        return extractSessionCookie(callbackResult) ?: authRequestCookie
    }

    // Spring Session writes the session id via a raw Set-Cookie header, which is not exposed through
    // MockHttpServletResponse.getCookies(), so parse the header directly.
    private fun extractSessionCookie(result: MvcResult): Cookie? {
        return result.response.getHeaders("Set-Cookie")
            .firstOrNull { it.startsWith("$sessionCookieName=") }
            ?.substringAfter("=")
            ?.substringBefore(";")
            ?.takeIf { it.isNotEmpty() }
            ?.let { Cookie(sessionCookieName, it) }
    }

    private fun getState(authenticationMvcResult: MvcResult): String {
        val location = authenticationMvcResult.response.getHeader("Location")
        val rawState = UriComponentsBuilder.fromUriString(location!!)
            .build()
            .queryParams
            .getFirst("state")
        return URLDecoder.decode(rawState, StandardCharsets.UTF_8)
    }
}
