/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.ContentResponse
import com.awakekt.awake.project.ContentTransport

/**
 * One `fetch()` call, as the browser interop makes it: a `GET` of [url] (resolved against [baseUrl]
 * when that is set, else against the page), in `cors` mode, with each of [headerNames] set to the
 * value at the same place in [headerValues].
 */
internal class FetchCall(
    val url: String,
    val baseUrl: String?,
    val headerNames: List<String>,
    val headerValues: List<String>,
)

/** What one `fetch()` call came to. */
internal sealed interface FetchOutcome {
    /**
     * A response, of any status. [flatHeaders] holds each header the browser exposes as a name and
     * then its value, as `Headers.forEach` yields them: names in lower case, repeats already joined.
     */
    class Answered(val status: Int, val flatHeaders: List<String>, val body: ByteArray) : FetchOutcome

    /** No response: `fetch()` rejected, with [reason] as the browser described it. */
    class Failed(val reason: String) : FetchOutcome
}

/**
 * The browser transport's logic, kept out of the wasm interop so it runs, and is tested, on every
 * target: [fetch] is the only part that touches the browser.
 */
internal class FetchContentTransport(
    private val baseUrl: String?,
    private val fetch: suspend (FetchCall) -> FetchOutcome,
) : ContentTransport {
    override suspend fun send(request: ContentRequest): ContentResponse =
        when (val outcome = fetch(fetchCallOf(request, baseUrl))) {
            is FetchOutcome.Answered -> contentResponseOf(request, outcome)
            is FetchOutcome.Failed -> throw fetchFailure(request, outcome.reason)
        }
}

/** The `fetch()` call that sends [request]: its URL as it is, with the token's query if it has one, and its headers in order. */
internal fun fetchCallOf(request: ContentRequest, baseUrl: String?): FetchCall {
    val headers = request.headers.entries.toList()
    return FetchCall(request.url, baseUrl, headers.map { it.key }, headers.map { it.value })
}

/** [outcome] as the [ContentResponse] to [request], every status included. */
internal fun contentResponseOf(request: ContentRequest, outcome: FetchOutcome.Answered): ContentResponse {
    require(outcome.flatHeaders.size % 2 == 0) { "Headers come as name and value pairs" }
    // Status 0 is an opaque response, whose status and body the browser hides; `cors` mode never
    // gets one, so it means the request didn't go as this transport sent it.
    if (outcome.status == 0) throw fetchFailure(request, "the browser hid the response (an opaque response, status 0)")
    val headers = outcome.flatHeaders.chunked(2).associate { (name, value) -> name to value }
    return ContentResponse(outcome.status, outcome.body, headers)
}

/**
 * The header names of [headers] a cross-origin content server must allow in its CORS preflight
 * answer: every one but `Accept`, `Accept-Language` and `Content-Language`. That is `Authorization`
 * and `Range`, for a content request. The Fetch standard now safelists a single plain `Range`, but
 * browsers that predate that still preflight it, so it is listed too.
 */
internal fun preflightHeaders(headers: Map<String, String>): List<String> =
    headers.keys.filter { it.lowercase() !in SAFELISTED_HEADERS }

/**
 * Why `fetch()` failed for [request], naming its URL without the query (where a read token may be)
 * and, since a browser reports a CORS refusal no differently from a network failure, what a
 * cross-origin content server must allow.
 */
internal fun fetchFailure(request: ContentRequest, reason: String): IllegalStateException {
    val preflight = preflightHeaders(request.headers)
    val noun = if (preflight.size == 1) "header" else "headers"
    val allowed = if (preflight.isEmpty()) "" else ", or the ${preflight.joinToString(" and ")} request $noun (Access-Control-Allow-Headers)"
    return IllegalStateException(
        "The browser couldn't fetch ${request.url.substringBefore('?')}: $reason. A cross-origin request fails this way when " +
            "the content server is unreachable, or its CORS answer doesn't allow this page's origin (Access-Control-Allow-Origin)$allowed",
    )
}

private val SAFELISTED_HEADERS = setOf("accept", "accept-language", "content-language")
