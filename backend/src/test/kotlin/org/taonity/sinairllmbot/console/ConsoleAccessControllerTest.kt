package org.taonity.sinairllmbot.console

import org.taonity.sinairllmbot.other.ControllerTestsBaseClass
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DirtiesContext
class ConsoleAccessControllerTest : ControllerTestsBaseClass() {

    @Test
    fun `access endpoint requires authentication`() {
        mockMvc.perform(get("/console/access/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrapped owner sees owner access`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/access/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.canView").value(true))
            .andExpect(jsonPath("$.canEdit").value(true))
            .andExpect(jsonPath("$.isAdmin").value(true))
            .andExpect(jsonPath("$.isOwner").value(true))
    }

    @Test
    fun `admin can list chat messages and audit logs`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/chat-messages").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mockMvc.perform(get("/console/audit-logs").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    fun `console endpoints reject anonymous access`() {
        mockMvc.perform(get("/console/chat-messages"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `owner can list users including self`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/users").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[?(@.email == 'test@example.com')].role").value("OWNER"))
    }
}
