package org.taonity.sinairllmbot.bot.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SafeUrlValidatorTest {
    private val validator = SafeUrlValidator()

    @Test
    fun `blocks loopback and private and metadata addresses`() {
        listOf(
            "http://127.0.0.1/",
            "http://localhost/",
            "http://10.0.0.5/",
            "http://192.168.1.10/",
            "http://172.16.4.4/",
            "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/",
            "http://0.0.0.0/",
        ).forEach { url ->
            assertThatThrownBy { validator.validate(url) }
                .`as`("should block $url")
                .isInstanceOf(UnsafeUrlException::class.java)
        }
    }

    @Test
    fun `rejects non http schemes`() {
        assertThatThrownBy { validator.validate("ftp://8.8.8.8/") }
            .isInstanceOf(UnsafeUrlException::class.java)
        assertThatThrownBy { validator.validate("file:///etc/passwd") }
            .isInstanceOf(UnsafeUrlException::class.java)
    }

    @Test
    fun `enforces https when required`() {
        assertThatThrownBy { validator.validate("http://8.8.8.8/", requireHttps = true) }
            .isInstanceOf(UnsafeUrlException::class.java)
    }

    @Test
    fun `allows public ip literal`() {
        val uri = validator.validate("https://8.8.8.8/path")
        assertThat(uri.host).isEqualTo("8.8.8.8")
    }
}

class ImageTypeDetectorTest {
    @Test
    fun `detects common image signatures`() {
        assertThat(ImageTypeDetector.detect(png())).isEqualTo("image/png")
        assertThat(ImageTypeDetector.detect(jpeg())).isEqualTo("image/jpeg")
        assertThat(ImageTypeDetector.detect(gif())).isEqualTo("image/gif")
        assertThat(ImageTypeDetector.detect(webp())).isEqualTo("image/webp")
    }

    @Test
    fun `returns null for non image bytes`() {
        assertThat(ImageTypeDetector.detect("<html>not an image</html>".toByteArray())).isNull()
        assertThat(ImageTypeDetector.detect(ByteArray(4))).isNull()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(16).also { arr ->
        values.forEachIndexed { i, v -> arr[i] = v.toByte() }
    }

    private fun png() = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private fun jpeg() = bytes(0xFF, 0xD8, 0xFF, 0xE0)
    private fun gif() = bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
    private fun webp() = bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)
}
