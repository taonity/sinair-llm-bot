package org.taonity.sinairllmbot.bot.ingestion

/**
 * Detects an image's real MIME type from its leading magic bytes rather than trusting the
 * server-supplied Content-Type header. Returns null when the bytes are not a recognised image,
 * which lets the fetcher reject disguised/non-image responses.
 */
object ImageTypeDetector {

    fun detect(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        return when {
            startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            startsWith(bytes, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(bytes, 0x47, 0x49, 0x46, 0x38) -> "image/gif"
            startsWith(bytes, 0x42, 0x4D) -> "image/bmp"
            startsWith(bytes, 0x52, 0x49, 0x46, 0x46) &&
                startsWith(bytes, 0x57, 0x45, 0x42, 0x50, offset = 8) -> "image/webp"
            else -> null
        }
    }

    private fun startsWith(bytes: ByteArray, vararg signature: Int, offset: Int = 0): Boolean {
        if (offset + signature.size > bytes.size) return false
        return signature.withIndex().all { (i, expected) -> (bytes[offset + i].toInt() and 0xFF) == expected }
    }
}
