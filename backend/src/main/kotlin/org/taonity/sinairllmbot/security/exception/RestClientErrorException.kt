package org.taonity.sinairllmbot.security.exception

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode

class RestClientErrorException(
    httpStatusCode: HttpStatusCode,
    method: HttpMethod,
    uri: String,
    requestHeadersJson: String,
    body: String
) : RuntimeException(buildMessage(httpStatusCode, method, uri, requestHeadersJson, body)) {
    companion object {
        fun buildMessage(
            httpStatusCode: HttpStatusCode,
            method: HttpMethod,
            uri: String,
            requestHeadersJson: String,
            body: String
        ): String {
            return "Request failed: $httpStatusCode, [$method] $uri, request headers: $requestHeadersJson, response body: [$body]"
        }
    }
}
