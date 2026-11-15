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
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readAvailable

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

    override suspend fun send(request: ContentRequest): ContentResponse =
        client.prepareGet(baseUrl?.let { resolveUrl(it, request.url) } ?: request.url) {
            expectSuccess = false
            headers { request.headers.forEach { (name, value) -> append(name, value) } }
        }.execute { response ->
            val headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") }
            ContentResponse(response.status.value, response.readBody(), headers)
        }
}

/**
 * The response's body, read from the connection straight into the array that is returned. `client.get`
 * and `bodyAsBytes` would keep the whole body in the saved response and copy it out again, which for a
 * content bundle is twice its size on the heap for each fetch in flight (#1619).
 *
 * `Content-Length` sizes the array when the server sends it; a body that turns out longer or shorter,
 * as a decompressed one does, is still read whole.
 */
private suspend fun HttpResponse.readBody(): ByteArray {
    val channel = bodyAsChannel()
    val expected = contentLength()?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
    var bytes = ByteArray(expected ?: UNSIZED_BODY_BYTES)
    var size = 0
    var read = 0
    while (read >= 0) {
        size += read
        // Full: either the body ends here, or it is longer than the array was sized for.
        if (size == bytes.size && !channel.exhausted()) bytes = bytes.copyOf(maxOf(bytes.size * 2, UNSIZED_BODY_BYTES))
        read = if (size < bytes.size) channel.readAvailable(bytes, size, bytes.size - size) else -1
    }
    return if (size == bytes.size) bytes else bytes.copyOf(size)
}

// What a body with no Content-Length starts at, and grows by doubling from.
private const val UNSIZED_BODY_BYTES = 64 * 1024
