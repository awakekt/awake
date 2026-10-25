/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

/**
 * The host's way of sending one content request, for [TokenAuthorizedFetch]: it sends
 * [ContentRequest.url] with every one of [ContentRequest.headers] and answers the HTTP status and
 * body, so Core carries no HTTP library.
 *
 * A transport answers every status it gets, 401 and 403 included, rather than throwing for them:
 * [TokenAuthorizedFetch] reads the status to tell a rejected token from a missing file. It resolves a
 * relative `url` against wherever the index came from, and keeps any retries and resumes of its own.
 * A host whose client can't send headers, such as one that loads through a URL alone, ignores
 * [ContentRequest.headers] and pairs with [ReadTokenPlacement.QueryParameter], which puts the token in
 * the URL instead.
 */
fun interface ContentTransport {
    /** Sends [request] and answers what came back, whatever its status. */
    suspend fun send(request: ContentRequest): ContentResponse
}

/**
 * One request [TokenAuthorizedFetch] asks a [ContentTransport] to send.
 *
 * Either [url] or [headers] carries a read token, so [toString] leaves out the URL's query and every
 * header value: a request a host logs never shows the token.
 *
 * @property url The address to read, with the token's query parameter when the token travels in the URL.
 * @property headers Header names and values to send, such as `Authorization`; empty when the token
 * travels in the URL.
 */
class ContentRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** The URL without its query and the header names without their values. */
    override fun toString(): String = "ContentRequest(url=${url.substringBefore('?')}, headers=${headers.keys})"
}

/**
 * What a [ContentTransport] got back for one [ContentRequest].
 *
 * @property status The HTTP status: 2xx is success, 401 and 403 mean the token was refused.
 * @property body The response body; for a success, the file's bytes.
 */
class ContentResponse(
    val status: Int,
    val body: ByteArray,
) {
    /** The status and the body's size, never its bytes. */
    override fun toString(): String = "ContentResponse(status=$status, ${body.size} bytes)"
}

/**
 * A content request [TokenAuthorizedFetch] could not complete: an HTTP status other than success, a
 * token still refused after one refresh, a token provider that failed, or a transport failure.
 *
 * Its message names [url] as the caller passed it, before any token was added, and never holds the
 * token itself.
 *
 * @property url The address as the caller passed it to [TokenAuthorizedFetch.fetch].
 * @property status The HTTP status that ended the request, or null when none arrived.
 */
class ContentFetchException(
    val url: String,
    val status: Int?,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
