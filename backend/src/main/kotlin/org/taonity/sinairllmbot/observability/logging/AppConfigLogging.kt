package org.taonity.sinairllmbot.observability.logging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Component
class AppConfigLogging(
    private val applicationContext: ApplicationContext,
    private val objectMapper: ObjectMapper,
) : CommandLineRunner {

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val ANY_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val SECRET_KEY_MARKERS = listOf("apikey", "secret", "password", "token", "credential")
        private const val OMITTED_KEY = "prompt"
    }

    override fun run(vararg args: String) {
        val appConfig = applicationContext
            .getBeansWithAnnotation(ConfigurationProperties::class.java)
            .values
            .mapNotNull { bean ->
                val prefix = configPrefix(bean) ?: return@mapNotNull null
                if (prefix != "app" && !prefix.startsWith("app.")) return@mapNotNull null
                prefix to sanitize(prefix, objectMapper.convertValue(bean, ANY_TYPE))
            }
            .sortedBy { it.first }
            .toMap(linkedMapOf())
        val payload = linkedMapOf<String, Any?>("event" to "app_config", "config" to appConfig)
        LOGGER.info { objectMapper.writeValueAsString(payload) }
    }

    private fun configPrefix(bean: Any): String? {
        val annotation = AnnotationUtils.findAnnotation(bean.javaClass, ConfigurationProperties::class.java)
            ?: return null
        return annotation.prefix.ifEmpty { annotation.value }
    }

    /** Recursively drops the omitted key and masks secret-looking values. */
    private fun sanitize(key: String, value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries
            .filterNot { (k, _) -> k.toString().equals(OMITTED_KEY, ignoreCase = true) }
            .associate { (k, v) -> k.toString() to sanitize(k.toString(), v) }
        is Iterable<*> -> value.map { sanitize(key, it) }
        else -> if (isSecretKey(key) && value is String) maskSecret(value) else value
    }

    private fun isSecretKey(key: String): Boolean {
        val normalized = key.lowercase()
        return SECRET_KEY_MARKERS.any { normalized.contains(it) }
    }

    /** Masks a secret keeping the first 3 and last 2 characters visible (fewer for short values). */
    private fun maskSecret(secret: String): String {
        if (secret.isEmpty()) return ""
        if (secret.length <= 4) return secret.take(1) + "*".repeat(secret.length - 1)
        val hidden = (secret.length - 5).coerceAtLeast(1)
        return secret.take(3) + "*".repeat(hidden) + secret.takeLast(2)
    }
}
