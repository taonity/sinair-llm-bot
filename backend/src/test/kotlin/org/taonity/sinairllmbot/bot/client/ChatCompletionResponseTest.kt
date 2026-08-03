package org.taonity.sinairllmbot.bot.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class ChatCompletionResponseTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `parses retryable provider error embedded in choice`() {
        val response = objectMapper.readValue(
            """{"choices":[{"finish_reason":null,"error":{"code":500,"message":"Provider failed"}}]}""",
            ChatCompletionResponse::class.java,
        )

        val error = response.choices.single().error!!
        assertEquals(500, error.code)
        assertEquals("Provider failed", error.message)
        assertTrue(error.shouldRetry(enabled = true))
        assertFalse(error.shouldRetry(enabled = false))
    }

    @Test
    fun `parses retryable top-level provider error`() {
        val response = objectMapper.readValue(
            """{"error":{"code":429,"message":"Rate limited"},"choices":[]}""",
            ChatCompletionResponse::class.java,
        )

        assertEquals(429, response.error?.code)
        assertTrue(response.error!!.shouldRetry(enabled = true))
    }

    @Test
    fun `client error is not retryable`() {
        val error = ChatCompletionResponse.ProviderError(code = 400, message = "Bad request")

        assertFalse(error.shouldRetry(enabled = true))
    }
}
