/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.ContentResponse
import com.awakekt.awake.project.ContentTransport
import com.awakekt.awake.project.TokenAuthorizedFetch
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsBytes

/**
 * Sends [TokenAuthorizedFetch]'s requests through a Ktor [HttpClient], for desktop, Android and iOS
 * (and a browser, through Ktor's Js engine).
 *
 * Each request is a `GET` of [ContentRequest.url], resolved against [baseUrl] when it is relative,
 * with every one of [ContentRequest.headers] set: the `Authorization` header that carries the read
 * token, and the `Range` header of a content bundle member read alone. Every status comes back as a
 * [ContentResponse], with the body and the response headers (`Retry-After` among them), and none is
 * thrown, even from a client built with `expectSuccess = true`: [TokenAuthorizedFetch] reads 401 and
 * 403 to refresh the token, and 429, or 503 with `Retry-After`, to back off as its `RateLimitBackoff`
 * says.
 *
 * The client is the host's: it picks the engine, and sets timeouts (`HttpTimeout`), proxies and
 * logging. Don't make Ktor's `HttpRequestRetry` retry a 429, or a 503 with `Retry-After`, too, or each wait is taken twice; network
 * failures are its to retry. A failure that reaches this transport, such as a refused connection or a
 * timeout, is thrown as Ktor throws it, and [TokenAuthorizedFetch] reports it as a
 * `ContentFetchException` with no status, the token taken out of its message. Cancelling the fetch
 * cancels the request. The transport never closes [client].
 *
 * @param client The host's Ktor client, with the engine and plugins it chose.
 * @param baseUrl The absolute URL a relative [ContentRequest.url] is resolved against, as a browser
 * resolves a link: usually the folder the project index was published to, ending in `/`. Null sends
 * each URL as it comes, for a client with its own `defaultRequest` URL or an index of absolute URLs.
 */
class KtorContentTransport(
    private val client: HttpClient,
    private val baseUrl: String? = null,
) : ContentTransport {
    init {
        require(baseUrl == null || hasScheme(baseUrl)) { "baseUrl must be absolute, with a scheme" }
    }

    override suspend fun send(request: ContentRequest): ContentResponse {
        val response = client.get(baseUrl?.let { resolveUrl(it, request.url) } ?: request.url) {
            expectSuccess = false
            headers { request.headers.forEach { (name, value) -> append(name, value) } }
        }
        val headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") }
        return ContentResponse(response.status.value, response.bodyAsBytes(), headers)
    }
}
