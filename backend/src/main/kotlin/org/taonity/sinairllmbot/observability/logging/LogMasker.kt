package org.taonity.sinairllmbot.observability.logging

import java.util.regex.Pattern

class LogMasker {

    private var multilinePattern: Pattern? = null
    private val maskPatterns = mutableListOf<String>()

    fun addMaskPattern(maskPattern: String) {
        maskPatterns.add(maskPattern)
        multilinePattern = Pattern.compile(
            maskPatterns.joinToString("|"),
            Pattern.MULTILINE
        )
    }

    fun mask(message: String): String =
        multilinePattern?.let { pattern ->
            StringBuilder(message).apply {
                val matcher = pattern.matcher(this)
                while (matcher.find()) {
                    (1..matcher.groupCount()).forEach { group ->
                        matcher.group(group)?.let {
                            maskSecretAtPosition(matcher.start(group), matcher.end(group), this)
                        }
                    }
                }
            }.toString()
        } ?: message

    private fun maskSecretAtPosition(start: Int, end: Int, sb: StringBuilder) {
        val length = end - start
        if (length > 8) {
            for (i in start + 2 until end - 2) {
                sb[i] = '*'
            }
        } else {
            for (i in start until end) {
                sb[i] = '*'
            }
        }
    }
}
