package org.taonity.sinairllmbot.other

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
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

    fun authorizeOAuth2(registrationId: String = "google-sinair-llm-bot"): MockHttpSession {
        val session = MockHttpSession()
        val authResult = mockMvc.perform(
            get("/oauth2/authorization/$registrationId").session(session)
        )
            .andExpect(status().is3xxRedirection)
            .andReturn()
        val state = getState(authResult)
        mockMvc.perform(
            get("/login/oauth2/code/$registrationId")
                .session(session)
                .param("code", "stub-auth-code")
                .param("state", state)
        )
            .andExpect(status().is3xxRedirection)
        return session
    }

    private fun getState(authenticationMvcResult: org.springframework.test.web.servlet.MvcResult): String {
        val location = authenticationMvcResult.response.getHeader("Location")
        val rawState = UriComponentsBuilder.fromUriString(location!!)
            .build()
            .queryParams
            .getFirst("state")
        return URLDecoder.decode(rawState, StandardCharsets.UTF_8)
    }
}
