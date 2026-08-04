package org.taonity.sinairllmbot.bot.grafana

import tools.jackson.databind.ObjectMapper

class CurrentEnvironmentLogQuery(
    containerPrefix: String,
    allowedServices: List<String>,
    private val objectMapper: ObjectMapper,
) {
    private val prefix = validateName(containerPrefix, "container prefix")
    private val services = allowedServices.map { validateName(it, "service") }.toSet()

    init {
        require(services.isNotEmpty()) { "At least one log service must be configured" }
    }

    fun build(search: String?, regex: Boolean, requestedServices: List<String>): String {
        val selectedServices = requestedServices.ifEmpty { services.toList() }
        require(selectedServices.all { it in services }) {
            "Only current-environment services are allowed: ${services.sorted().joinToString(", ")}"
        }
        val containerPattern = buildString {
            append('^').append(escapeRegex(prefix)).append("-(?:")
            append(selectedServices.distinct().sorted().joinToString("|") { escapeRegex(it) })
            append(")-[0-9]+$")
        }
        return buildString {
            append("{container=~").append(objectMapper.writeValueAsString(containerPattern)).append('}')
            search?.takeIf { it.isNotBlank() }?.let {
                append(if (regex) " |~ " else " |= ")
                append(objectMapper.writeValueAsString(it))
            }
        }
    }

    private fun validateName(value: String, kind: String): String = value.trim().also {
        require(NAME.matches(it)) { "Invalid $kind '$it'" }
    }

    private fun escapeRegex(value: String): String = buildString {
        value.forEach { character ->
            if (character in REGEX_META) append('\\')
            append(character)
        }
    }

    private companion object {
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")
        val REGEX_META = setOf('\\', '.', '+', '*', '?', '(', ')', '|', '[', ']', '{', '}', '^', '$')
    }
}