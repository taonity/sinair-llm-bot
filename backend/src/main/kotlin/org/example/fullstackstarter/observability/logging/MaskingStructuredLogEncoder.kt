package org.example.fullstackstarter.observability.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import org.springframework.boot.logging.logback.StructuredLogEncoder

class MaskingStructuredLogEncoder : StructuredLogEncoder() {

    private val masker = LogMasker()

    fun addMaskPattern(maskPattern: String) {
        masker.addMaskPattern(maskPattern)
    }

    override fun encode(event: ILoggingEvent): ByteArray {
        val raw = super.encode(event)
        val masked = masker.mask(String(raw, Charsets.UTF_8))
        val cleaned = masked.replace(Regex(",\"level_value\":\\d+|\"level_value\":\\d+,"), "")
        return cleaned.toByteArray(Charsets.UTF_8)
    }
}
