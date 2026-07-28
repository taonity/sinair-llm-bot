package org.taonity.sinairllmbot.bot.context

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextResultSanitizerTest {
    private val sanitizer = ContextResultSanitizer()

    @Test
    fun `redacts common credential shapes and enforces a result budget`() {
        val raw = """
            {"api_key":"sk-secret","authorization":"Bearer abc.def","url":"https://x.test?a=1&token=hidden"}
        """.trimIndent()

        val result = sanitizer.sanitize(raw, 80)

        assertThat(result).doesNotContain("sk-secret", "abc.def", "hidden")
        assertThat(result).contains("[REDACTED]")
        assertThat(result.length).isLessThanOrEqualTo(95)
    }
}
