package org.taonity.sinairllmbot.bot.ingestion

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.taonity.sinairllmbot.bot.LogRedact
import org.taonity.sinairllmbot.bot.ingestion.config.IngestionSettings
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Streaming GET with defence-in-depth for fetching arbitrary user-supplied URLs:
 *  - redirects are never auto-followed; each hop (including the original) is SSRF-validated,
 *  - a hard byte cap aborts the read so an oversized "trap" response can't exhaust memory,
 *  - connect and request timeouts bound how long a slow host can stall the bot.
 */
@Component
class SafeHttpFetcher(
    private val validator: SafeUrlValidator,
    private val settings: IngestionSettings,
) {
    private val properties get() = settings.ingestion()

    private companion object {
        private val LOGGER = KotlinLogging.logger {}
        private const val BUFFER_SIZE = 8192
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
        .build()

    data class FetchResult(
        val finalUrl: String,
        val contentType: String?,
        val body: ByteArray,
    )

    fun fetch(rawUrl: String, requireHttps: Boolean, maxBytes: Long, accept: String? = null): FetchResult {
        var currentUrl = rawUrl
        repeat(properties.maxRedirects + 1) {
            val uri = validator.validate(currentUrl, requireHttps)
            val response = send(uri, accept)
            val status = response.statusCode()

            if (status in 300..399) {
                val location = response.headers().firstValue("location").orElse(null)
                response.body().close()
                if (location.isNullOrBlank()) {
                    throw IngestionException("Redirect ($status) without Location from $currentUrl")
                }
                currentUrl = uri.resolve(location).toString()
                LOGGER.debug { "Following redirect [${LogRedact.urlToken(currentUrl)}]" }
                return@repeat
            }
            if (status !in 200..299) {
                response.body().close()
                throw IngestionException("HTTP $status fetching $currentUrl")
            }

            val contentType = response.headers().firstValue("content-type").orElse(null)
            val body = readCapped(response.body(), maxBytes, currentUrl)
            return FetchResult(finalUrl = currentUrl, contentType = contentType, body = body)
        }
        throw IngestionException("Too many redirects fetching $rawUrl")
    }

    private fun send(uri: URI, accept: String?): HttpResponse<InputStream> {
        val builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(properties.fetchTimeoutSeconds))
            .header("User-Agent", properties.userAgent)
            .GET()
        accept?.let { builder.header("Accept", it) }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    }

    private fun readCapped(stream: InputStream, maxBytes: Long, url: String): ByteArray {
        stream.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IngestionException("Response body exceeds cap of $maxBytes bytes for $url")
                }
                buffer.write(chunk, 0, read)
            }
            return buffer.toByteArray()
        }
    }
}
