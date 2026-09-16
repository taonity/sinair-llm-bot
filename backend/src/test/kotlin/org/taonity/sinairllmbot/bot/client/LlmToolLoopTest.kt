package org.taonity.sinairllmbot.bot.client

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.taonity.sinairllmbot.bot.config.LlmProperties
import org.taonity.sinairllmbot.bot.pipeline.PipelineLlmUsageTracker
import org.taonity.sinairllmbot.config.BotSettings
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.InetSocketAddress

class LlmToolLoopTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `empty reasoning exhaustion receives a larger final allowance`() {
        withProvider(listOf(response("", "length"), response("Completed answer"))) { client, requests ->
            val result = client.completeWithTools("reply", listOf(ChatMessage.user("Investigate")), emptyList(), 20, { _, _ -> error("no tools") })
            assertThat(result?.content).isEqualTo("Completed answer")
            assertThat(requests.map { it["max_tokens"].asInt() }).containsExactly(1500, 6000)
            assertThat(requests.last()["tools"].isArray).isTrue()
        }
    }

    @Test
    fun `truncated final answer gets recovery rather than being accepted`() {
        withProvider(listOf(response("unfinished", "length"), response("Complete answer"))) { client, requests ->
            val result = client.completeWithTools("reply", listOf(ChatMessage.user("Explain")), emptyList(), 0, { _, _ -> "" })
            assertThat(result?.content).isEqualTo("Complete answer")
            assertThat(requests.map { it["max_tokens"].asInt() }).containsExactly(6000, 12000)
        }
    }

    @Test
    fun `reasoning is preserved and read results are reused`() {
        val toolResponse = """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","reasoning_details":[{"type":"reasoning.encrypted","data":"opaque"}],"tool_calls":[{"id":"one","type":"function","function":{"name":"read","arguments":"{}"}}]}}]}"""
        withProvider(listOf(toolResponse, toolResponse, response("Answer"))) { client, requests ->
            var executions = 0
            client.completeWithTools("reply", listOf(ChatMessage.user("Investigate")), emptyList(), 3, { _, _ -> executions++; "evidence" }, setOf("read"))
            assertThat(executions).isEqualTo(1)
            assertThat(requests[1]["messages"][1]["reasoning_details"][0]["data"].asString()).isEqualTo("opaque")
        }
    }

    @Test
    fun `truncated tool requests never execute`() {
        val truncatedTool = """{"choices":[{"finish_reason":"length","message":{"role":"assistant","tool_calls":[{"id":"one","type":"function","function":{"name":"write","arguments":"{}"}}]}}]}"""
        withProvider(listOf(truncatedTool, response("Could not complete action"))) { client, _ ->
            client.completeWithTools("reply", listOf(ChatMessage.user("Act")), emptyList(), 3, { _, _ -> error("must not execute") })
        }
    }

    private fun response(content: String, finish: String = "stop") = mapper.writeValueAsString(
        mapOf("choices" to listOf(mapOf("finish_reason" to finish, "message" to mapOf("role" to "assistant", "content" to content)))),
    )

    @Test
    fun `twentieth call exhaustion still completes the last tool and final answer`() {
        val toolResponse = """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","tool_calls":[{"id":"step","type":"function","function":{"name":"read","arguments":"{}"}}]}}]}"""
        val replies = List(19) { toolResponse } + response("", "length") + toolResponse + response("Investigation complete")
        withProvider(replies) { client, requests ->
            val result = client.completeWithTools("reply", listOf(ChatMessage.user("Investigate")), emptyList(), 20, { _, _ -> "evidence" }, setOf("read"))
            assertThat(result?.content).isEqualTo("Investigation complete")
            assertThat(requests).hasSize(22)
            assertThat(requests[19]["max_tokens"].asInt()).isEqualTo(1500)
            assertThat(requests[20]["max_tokens"].asInt()).isEqualTo(6000)
            assertThat(requests.last().has("tools")).isFalse()
        }
    }

    private fun withProvider(responses: List<String>, action: (LlmClient, List<JsonNode>) -> Unit) {
        val requests = mutableListOf<JsonNode>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            requests.add(mapper.readTree(exchange.requestBody))
            val body = responses.getOrElse(requests.lastIndex) { response("Unexpected extra call") }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val settings = mock(BotSettings::class.java)
            val properties = mock(LlmProperties::class.java)
            `when`(settings.llm()).thenReturn(properties)
            `when`(properties.baseUrl).thenReturn("http://127.0.0.1:${server.address.port}")
            `when`(properties.apiKey).thenReturn("test")
            `when`(properties.timeoutSeconds).thenReturn(5)
            `when`(properties.retry).thenReturn(LlmProperties.Retry(1, 0, false))
            `when`(properties.tier("reply")).thenReturn(LlmProperties.Tier("test/model", 0.0, 1500))
            `when`(properties.toolLoop).thenReturn(LlmProperties.ToolLoop("", 20, 6000, 12000, 120000, 12000, 60, "low"))
            action(LlmClient(settings, mapper, mock(PipelineLlmUsageTracker::class.java)), requests)
        } finally {
            server.stop(0)
        }
    }
}