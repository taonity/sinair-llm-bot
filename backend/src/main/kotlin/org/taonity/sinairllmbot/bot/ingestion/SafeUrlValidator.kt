package org.taonity.sinairllmbot.bot.ingestion

import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI

/**
 * SSRF guard. A chat message can contain any URL, so before fetching we reject anything that could
 * be used to reach internal/metadata endpoints:
 *  - scheme must be http/https (https-only when [requireHttps]),
 *  - host must resolve, and none of its addresses may be loopback, link-local (incl. cloud
 *    metadata 169.254.169.254), site-local/private, unique-local IPv6, any-local or multicast.
 *
 * Every redirect hop is re-validated by the caller (see [SafeHttpFetcher]). There is a small
 * resolve-then-connect TOCTOU window (DNS rebinding) that is acceptable for this MVP.
 */
@Component
class SafeUrlValidator {

    fun validate(rawUrl: String, requireHttps: Boolean = false): URI {
        val uri = try {
            URI(rawUrl)
        } catch (exception: Exception) {
            throw UnsafeUrlException("Malformed URL: $rawUrl")
        }

        val scheme = uri.scheme?.lowercase()
            ?: throw UnsafeUrlException("URL has no scheme: $rawUrl")
        if (requireHttps) {
            if (scheme != "https") throw UnsafeUrlException("Only https:// is allowed here: $rawUrl")
        } else if (scheme != "http" && scheme != "https") {
            throw UnsafeUrlException("Unsupported scheme '$scheme': $rawUrl")
        }

        val host = uri.host ?: throw UnsafeUrlException("URL has no host: $rawUrl")
        if (host.equals("localhost", ignoreCase = true)) {
            throw UnsafeUrlException("Blocked host: $host")
        }

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (exception: Exception) {
            throw UnsafeUrlException("Cannot resolve host: $host")
        }
        addresses.forEach { address ->
            if (isBlocked(address)) {
                throw UnsafeUrlException("Blocked internal/private address for $host (${address.hostAddress})")
            }
        }
        return uri
    }

    private fun isBlocked(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            isUniqueLocalIpv6(address)

    /** IPv6 unique-local range fc00::/7, which `isSiteLocalAddress` does not cover. */
    private fun isUniqueLocalIpv6(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
