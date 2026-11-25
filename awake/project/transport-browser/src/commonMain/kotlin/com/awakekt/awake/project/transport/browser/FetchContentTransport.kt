/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.ContentResponse
import com.awakekt.awake.project.ContentTransport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    /**
     * No response: `fetch()` rejected or timed out, with [reason] as the browser described it.
     * [retryable] is false for permanent client-side failures such as an unparseable URL.
     */
    class Failed(val reason: String, val retryable: Boolean = true) : FetchOutcome
}

/**
 * The browser transport's logic, kept out of the wasm interop so it runs, and is tested, on every
 * target: [fetch] is the only part that touches the browser.
 *
 * Retries transient [FetchOutcome.Failed] outcomes and retryable HTTP statuses
 * (`408, 429, 500, 502, 503, 504`) up to [BrowserTransportOptions.maxAttempts] times with
 * exponential backoff (or `Retry-After` delta-seconds when present and within [BrowserTransportOptions.totalTimeoutMs]).
 */
internal class FetchContentTransport(
    private val baseUrl: String?,
    private val options: BrowserTransportOptions,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val fetch: suspend (FetchCall) -> FetchOutcome,
) : ContentTransport {
    /** Single-attempt transport preserving pre-retry behavior for callers without [BrowserTransportOptions]. */
    constructor(
        baseUrl: String?,
        fetch: suspend (FetchCall) -> FetchOutcome,
    ) : this(baseUrl, BrowserTransportOptions(maxAttempts = 1), fetch = fetch)

    override suspend fun send(request: ContentRequest): ContentResponse {
        val call = fetchCallOf(request, baseUrl)
        var attempt = 1
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val outcome = fetch(call)) {
                is FetchOutcome.Answered -> {
                    val response = contentResponseOf(request, outcome)
                    if (attempt >= options.maxAttempts || response.status !in RETRYABLE_HTTP_STATUSES) {
                        return response
                    }
                    val waitMs = retryWaitMs(attempt, response) ?: return response
                    if (waitMs > 0L) delay(waitMs)
                }
                is FetchOutcome.Failed -> {
                    if (!outcome.retryable || attempt >= options.maxAttempts) {
                        throw fetchFailure(request, outcome.reason)
                    }
                    val waitMs = retryBackoffDelayMs(attempt, options.retryBaseDelayMs)
                    if (waitMs > 0L) delay(waitMs)
                }
            }
            attempt++
        }
    }

    private fun retryWaitMs(attempt: Int, response: ContentResponse): Long? {
        val retryAfterMs = response.header(RETRY_AFTER_HEADER)?.let(::parseRetryAfterSecondsMs)
        if (retryAfterMs != null) {
            return retryAfterMs.takeIf { it <= options.totalTimeoutMs }
        }
        return retryBackoffDelayMs(attempt, options.retryBaseDelayMs)
    }
}

/**
 * Exponential backoff delay for retry [attempt] (1-based), doubling [baseDelayMs] per retry and
 * capped to avoid unbounded waits or overflow.
 */
internal fun retryBackoffDelayMs(attempt: Int, baseDelayMs: Long): Long {
    if (baseDelayMs <= 0L) return 0L
    val shift = (attempt - 1).coerceIn(0, MAX_BACKOFF_SHIFT)
    val multiplier = 1L shl shift
    val scaled = if (baseDelayMs > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else baseDelayMs * multiplier
    return minOf(scaled, maxOf(baseDelayMs, MAX_BACKOFF_DELAY_MS))
}

/**
 * Parses a numeric `Retry-After` delta-seconds header [value] into milliseconds, or null when
 * [value] is not a non-negative integer. Values whose millisecond equivalent exceeds [Long.MAX_VALUE]
 * saturate at [Long.MAX_VALUE].
 */
internal fun parseRetryAfterSecondsMs(value: String): Long? {
    val trimmed = value.trim()
    val seconds = if (trimmed.isNotEmpty() && trimmed.all { it in '0'..'9' }) {
        trimmed.toLongOrNull() ?: Long.MAX_VALUE
    } else {
        null
    }
    return seconds?.let {
        if (it > Long.MAX_VALUE / MILLIS_PER_SECOND) Long.MAX_VALUE else it * MILLIS_PER_SECOND
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
private val RETRYABLE_HTTP_STATUSES = setOf(408, 429, 500, 502, 503, 504)
private const val RETRY_AFTER_HEADER = "Retry-After"
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_BACKOFF_SHIFT = 20
private const val MAX_BACKOFF_DELAY_MS = 30_000L
