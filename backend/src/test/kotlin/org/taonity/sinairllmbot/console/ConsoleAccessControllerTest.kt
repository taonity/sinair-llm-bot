package org.taonity.sinairllmbot.console

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.taonity.sinairllmbot.other.ControllerTestsBaseClass
import org.junit.jupiter.api.Test
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.taonity.sinairllmbot.bot.entity.PipelineRunEntity
import org.taonity.sinairllmbot.bot.repository.PipelineRunRepository

@DirtiesContext
class ConsoleAccessControllerTest : ControllerTestsBaseClass() {

    @Autowired
    private lateinit var pipelineRunRepository: PipelineRunRepository

    @Test
    fun `access endpoint requires authentication`() {
        mockMvc.perform(get("/console/access/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrapped owner sees owner access`() {
        val session = authorizeOAuth2()

        mockMvc.perform(get("/console/access/me").cookie(session))
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

        mockMvc.perform(get("/console/chat-messages").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mockMvc.perform(get("/console/audit-logs").cookie(session))
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

        mockMvc.perform(get("/console/users").cookie(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[?(@.email == 'test@example.com')].role").value("OWNER"))
    }

    @Test
    fun `viewer can export complete pipeline debug bundle`() {
        val session = authorizeOAuth2()
        val run = pipelineRunRepository.save(
            PipelineRunEntity(
                pipelineKey = "reply",
                roomTarget = "room-1",
                triggerSenderLogin = "alice",
                triggerText = "Why did this fail?",
                outcome = "FAILED",
                outcomeDetail = "model error",
                stagesJson = """[{"key":"triage","label":"Triage","status":"STOP","summary":"failed","fields":[],"alternatives":[]}]""",
                totalTokens = 12,
                llmUsageJson = """[{"tier":"gate","model":"test/model","tokens":12,"requestPayload":"{\"prompt\":\"hello\"}","responsePayload":"{\"error\":\"bad\"}"}]""",
                jsonParseFailureCount = 1,
                jsonParseFailuresJson = """[{"label":"triage","attempt":1,"payload":"not json"}]""",
                configRevisionId = "revision-1",
                contextManifestJson = """{"sources":["docs/TESTING.md"]}""",
            ),
        )

        mockMvc.perform(get("/console/pipeline-runs/${run.id}/export").cookie(session))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pipeline-${run.id}.md\""))
            .andExpect(content().contentType("text/markdown;charset=UTF-8"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("# Pipeline debug bundle")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Why did this fail?")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("docs/TESTING.md")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"prompt\" : \"hello\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"error\" : \"bad\"")))

        mockMvc.perform(get("/console/pipeline-runs/${run.id}/export"))
            .andExpect(status().isUnauthorized)
    }
}
