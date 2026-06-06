package org.example.fullstackstarter.observability.logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent

class MaskingPatternLayout : PatternLayout() {

    private val masker = LogMasker()

    fun addMaskPattern(maskPattern: String) {
        masker.addMaskPattern(maskPattern)
    }

    override fun doLayout(event: ILoggingEvent): String {
        return masker.mask(super.doLayout(event))
    }
}
