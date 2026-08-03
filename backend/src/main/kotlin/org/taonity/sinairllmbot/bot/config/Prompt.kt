package org.taonity.sinairllmbot.bot.config

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Component

data class Prompt(val text: String)

@Component
@ConfigurationPropertiesBinding
class PromptConverter : Converter<String, Prompt> {
    override fun convert(source: String): Prompt {
        val resource = DefaultResourceLoader().getResource(source)
        require(resource.exists()) { "Prompt resource '$source' does not exist" }
        val text = resource.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val normalizedText = text.replace("\r\n", "\n").replace('\r', '\n')
        return Prompt(normalizedText.trim().split(Regex("\n(?:[\\t ]*\n)+")).joinToString("\n\n") { paragraph ->
            paragraph.lines().joinToString(" ") { it.trim() }
        })
    }
}