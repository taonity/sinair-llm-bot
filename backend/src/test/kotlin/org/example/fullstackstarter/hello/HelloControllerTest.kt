package org.example.fullstackstarter.hello

import org.example.fullstackstarter.other.ControllerTestsBaseClass
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DirtiesContext
class HelloControllerTest : ControllerTestsBaseClass() {

    @Test
    fun `root endpoint is publicly accessible`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }

    @Test
    fun `hello endpoint requires authentication`() {
        mockMvc.perform(get("/hello"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `hello endpoint returns greeting for authenticated user`() {
        val mockHttpSession = authorizeOAuth2()

        mockMvc.perform(
            get("/hello").session(mockHttpSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Hello, Test User!"))
            .andExpect(jsonPath("$.email").value("test@example.com"))
    }
}
