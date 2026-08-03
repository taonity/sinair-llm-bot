package org.taonity.sinairllmbot.bot.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptConverterTest {
    @Test
    fun `joins wrapped lines and preserves paragraphs`() {
        val prompt = PromptConverter().convert("classpath:prompts/wrapped-test.txt")

        assertThat(prompt.text).isEqualTo(
            "First paragraph wraps across multiple source lines.\n\nSecond paragraph stays separate.",
        )
    }
}