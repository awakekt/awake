/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Reads a published project's index and files with a short-lived read token attached, which a game
 * gets however it likes (for example from its own server after its player signs in) and hands over
 * through [tokenProvider].
 *
 * Each [fetch] attaches the current token as [placement] says and sends the request through the host's
 * [transport]. The token is asked for on the first fetch and again once it is within [refreshAhead] of
 * expiring (or half its lifetime, for a token shorter-lived than twice [refreshAhead]), so a long
 * session never sends an expired one. A request answered 401 or 403 gets a fresh token and is sent once
 * more; refused again, it fails with a [ContentFetchException] that says so. Requests running at once,
 * such as [IndexedAssetSource.prefetch]'s, share one refresh: one call to [tokenProvider], whose token,
 * or failure, every waiting request gets.
 *
 * A request answered 429 Too Many Requests, or 503 Service Unavailable with a `Retry-After`, is waited
 * out and sent again as [rateLimitBackoff] says: for as long as the response's `Retry-After` asks, or
 * for a 429 that says nothing, with a jittered exponential delay, a bounded number of times and for a
 * bounded total wait, after which the answer fails the fetch as a [ContentFetchException] with
 * status 429 or 503. A 503 without `Retry-After` fails the fetch at once with status 503. Each wait
 * suspends; none blocks a thread.
 *
 * The token reaches only the requests [transport] sends. It is never part of an exception message
 * (a transport failure that quotes it is reported with the token taken out), never logged, and never
 * reaches a [ContentCache]: [IndexedAssetSource] keys and stores files by their SHA-256 and checked
 * bytes, so a new token, or a new release, finds the files it already has.
 *
 * Pass [fetch] as [IndexedAssetSource]'s `fetch` (and [fetchRange] as its `rangeFetch`, to read one
 * content bundle member at a time), or this whole object to [IndexedAssetSource.Companion.open], which
 * reads the index through it too.
 *
 * @param transport The host's HTTP client, behind [ContentTransport].
 * @param placement Where the token goes: an `Authorization: Bearer` header, the default, or a query
 * parameter for a host that can't send headers.
 * @param refreshAhead How long before the token expires to ask for the next one. Not negative.
 * @param timeSource The clock that measures a token's lifetime; monotonic, so a change to the
 * device's wall clock doesn't expire a token early or keep one late.
 * @param rateLimitBackoff How a 429 answer, or a 503 with `Retry-After`, is waited out and retried;
 * [RateLimitBackoff.None] fails the fetch at the first one.
 * @param tokenProvider Answers a current read token. Called once per refresh, never twice at once,
 * while requests wait on it, so it must not call [fetch] itself. What it throws fails the requests
 * waiting on it as a [ContentFetchException]; the next request asks again.
 */
class TokenAuthorizedFetch(
    private val transport: ContentTransport,
    private val placement: ReadTokenPlacement = ReadTokenPlacement.AuthorizationHeader,
    private val refreshAhead: Duration = DEFAULT_REFRESH_AHEAD,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val rateLimitBackoff: RateLimitBackoff = RateLimitBackoff(),
    private val tokenProvider: suspend () -> ReadToken,
) {
    private class HeldToken(val token: ReadToken, val refreshAt: TimeMark)

    /** One fetch's 429 and 503 answers and the time spent waiting them out, across its sends. */
    private class RateLimitTally {
        var answered = 0
        var waited = Duration.ZERO
    }

    private val lock = Mutex()
    private var held: HeldToken? = null
    private var lastFailure: Throwable? = null

    // Read before waiting for the lock, so a request can tell a refresh that finished while it waited.
    @Volatile
    private var refreshes = 0L

    init {
        require(!refreshAhead.isNegative()) { "refreshAhead must not be negative: $refreshAhead" }
    }

    /**
     * Reads [url] with the current read token attached, refreshing the token first when it is about
     * to expire, and once more if the request is refused with 401 or 403.
     *
     * @param url The address to read, as [transport] resolves it; never holding a token itself.
     * @return The response body of a 2xx answer.
     * @throws ContentFetchException for any other status, a second refusal, a failed [tokenProvider],
     * or a [transport] failure; its message names [url] and never the token.
     */
    suspend fun fetch(url: String): ByteArray = bodyOf(url, authorized(url, emptyMap()))

    /**
     * Reads [length] bytes from [offset] of the file at [url], with the read token attached as
     * [fetch] attaches it, through an HTTP `Range` request: what [IndexedAssetSource] uses to read one
     * member of a content bundle without the whole bundle.
     *
     * A 206 answer must hold exactly the range. A 200 answer, from a server that ignores ranges or a
     * [transport] that can't send headers, holds the whole file, and the range is cut from it.
     *
     * @param url The address to read, as [transport] resolves it; never holding a token itself.
     * @param offset The first byte to read. Not negative.
     * @param length How many bytes to read. Not negative; zero reads nothing and sends no request.
     * @return The [length] bytes from [offset].
     * @throws ContentFetchException for any status but 206 or 200, an answer of the wrong size, or
     * whatever [fetch] throws for.
     */
    suspend fun fetchRange(url: String, offset: Long, length: Long): ByteArray {
        require(offset >= 0 && length >= 0) { "A range needs a non-negative offset and length: $offset, $length" }
        if (length == 0L) return ByteArray(0)
        val end = offset + length
        val response = authorized(url, mapOf(RANGE_HEADER to "bytes=$offset-${end - 1}"))
        val body = response.body
        val range = when (response.status) {
            PARTIAL_CONTENT -> body.takeIf { it.size.toLong() == length }
            OK -> if (body.size >= end) body.copyOfRange(offset.toInt(), end.toInt()) else null
            else -> throw ContentFetchException(url, response.status, "$url answered HTTP ${response.status}")
        }
        return range ?: throw ContentFetchException(
            url,
            response.status,
            "$url answered ${body.size} bytes with HTTP ${response.status}, which don't hold bytes $offset to ${end - 1}",
        )
    }

    /** Sends [url] with [headers] and the token, refreshing it and sending again once if it is refused. */
    private suspend fun authorized(url: String, headers: Map<String, String>): ContentResponse {
        val tally = RateLimitTally()
        val (token, response) = sendPaced(url, headers, refused = null, tally)
        if (response.status !in REFUSED_STATUSES) return response
        val (_, retried) = sendPaced(url, headers, refused = token, tally)
        if (retried.status in REFUSED_STATUSES) {
            throw ContentFetchException(
                url,
                retried.status,
                "$url was refused with HTTP ${retried.status} again after the read token was refreshed, " +
                    "so the token provider's tokens don't grant access to it",
            )
        }
        return retried
    }

    /**
     * Sends [url] with the current token, past a [refused] one, waiting out each 429, and each 503 with
     * a `Retry-After`, as [rateLimitBackoff] says and sending again with the token checked anew.
     * Answers the token last sent and the response that wasn't one of those.
     */
    private suspend fun sendPaced(
        url: String,
        headers: Map<String, String>,
        refused: ReadToken?,
        tally: RateLimitTally,
    ): Pair<ReadToken, ContentResponse> {
        var token = currentToken(url, refused)
        while (true) {
            val response = send(url, token, headers)
            val retryAfter = response.header(RETRY_AFTER_HEADER)
            if (!rateLimitBackoff.backsOffOn(response.status, retryAfter)) return token to response
            tally.answered++
            when (val step = rateLimitBackoff.next(tally.answered, retryAfter, tally.waited)) {
                is RateLimitStep.Stop -> throw ContentFetchException(
                    url,
                    response.status,
                    "$url was answered HTTP ${response.status} ${reasonPhrase(response.status)} and not sent again: ${step.reason}",
                )
                is RateLimitStep.Wait -> {
                    rateLimitBackoff.pause(step.duration)
                    tally.waited += step.duration
                }
            }
            // The wait may have brought the token close to expiring; another request may have refreshed it.
            token = currentToken(url, refused)
        }
    }

    private fun reasonPhrase(status: Int): String =
        if (status == RateLimitBackoff.SERVICE_UNAVAILABLE) "Service Unavailable" else "Too Many Requests"

    private suspend fun currentToken(url: String, refused: ReadToken?): ReadToken {
        val refreshesOnArrival = refreshes
        val outcome = lock.withLock {
            val current = held
            val failure = lastFailure
            when {
                // Another request may have refreshed already, after this one's token was refused.
                current != null && current.token !== refused && current.refreshAt.hasNotPassedNow() ->
                    Result.success(current.token)
                // A refresh that was running when this request arrived failed: share its failure
                // rather than asking the provider again for every waiting request.
                failure != null && refreshes != refreshesOnArrival -> Result.failure(failure)
                else -> refresh()
            }
        }
        return outcome.getOrElse { throw ContentFetchException(url, null, "Couldn't get a read token to fetch $url", it) }
    }

    /** Called with [lock] held. */
    private suspend fun refresh(): Result<ReadToken> {
        val outcome = runCatching { tokenProvider() }
        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        lastFailure = outcome.exceptionOrNull()
        outcome.getOrNull()?.let { token ->
            val lifetime = token.expiresIn.coerceAtLeast(Duration.ZERO)
            held = HeldToken(token, timeSource.markNow() + (lifetime - minOf(refreshAhead, lifetime / 2)))
        }
        refreshes++
        return outcome
    }

    private suspend fun send(url: String, token: ReadToken, headers: Map<String, String>): ContentResponse {
        val request = when (placement) {
            ReadTokenPlacement.AuthorizationHeader ->
                ContentRequest(url, headers + (AUTHORIZATION_HEADER to "Bearer ${token.value}"))
            is ReadTokenPlacement.QueryParameter ->
                ContentRequest(withQueryParameter(url, placement.name, token.value), headers)
        }
        val outcome = runCatching { transport.send(request) }
        val failure = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
        if (failure is CancellationException) throw failure
        throw transportFailure(url, token, failure)
    }

    private fun transportFailure(url: String, token: ReadToken, failure: Throwable): ContentFetchException {
        val secrets = listOf(token.value, encodeQueryComponent(token.value))
        val chain = generateSequence(failure) { it.cause }.take(MAX_CAUSES_DESCRIBED).toList()
        val quotesToken = chain.any { cause -> secrets.any { cause.message.orEmpty().contains(it) } }
        if (!quotesToken) return ContentFetchException(url, null, "Fetching $url failed: ${failure.message}", failure)
        // The cause would carry the token into any stack trace a host prints, so it is described,
        // with the token taken out, instead of attached.
        val described = chain.joinToString(": ") { it.message ?: (it::class.simpleName ?: "error") }
        val redacted = secrets.fold(described) { text, secret -> text.replace(secret, REDACTED) }
        return ContentFetchException(url, null, "Fetching $url failed: $redacted")
    }

    private fun bodyOf(url: String, response: ContentResponse): ByteArray {
        if (response.status !in SUCCESS_STATUSES) {
            throw ContentFetchException(url, response.status, "$url answered HTTP ${response.status}")
        }
        return response.body
    }

    /** Defaults for [TokenAuthorizedFetch]. */
    companion object {
        /** How long before a token expires [TokenAuthorizedFetch] asks for the next one, by default. */
        val DEFAULT_REFRESH_AHEAD: Duration = 60.seconds

        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val RANGE_HEADER = "Range"
        private const val RETRY_AFTER_HEADER = "Retry-After"
        private const val OK = 200
        private const val PARTIAL_CONTENT = 206
        private const val REDACTED = "<redacted>"
        private const val MAX_CAUSES_DESCRIBED = 8
        private val SUCCESS_STATUSES = 200..299
        private val REFUSED_STATUSES = setOf(401, 403)
    }
}

/**
 * A short-lived token that grants reading a published project's content, as a game's token provider
 * hands it to [TokenAuthorizedFetch].
 *
 * [toString] leaves [value] out, so a token that ends up in a log line shows only its lifetime.
 *
 * @property value The token itself, sent as a bearer token or a query parameter: not empty, with no
 * whitespace or control characters.
 * @property expiresIn How long the token stays valid from when the provider answers it, such as an
 * OAuth `expires_in`, or a JWT's `exp` less the current time. Zero or negative means it is used for the
 * request that asked for it and replaced on the next one.
 */
class ReadToken(
    val value: String,
    val expiresIn: Duration,
) {
    init {
        require(value.isNotEmpty() && value.none { it.isWhitespace() || it.isISOControl() }) {
            "A read token must be non-empty, with no whitespace or control characters"
        }
    }

    /** The lifetime only; never the token. */
    override fun toString(): String = "ReadToken(expiresIn=$expiresIn)"
}

/** Where [TokenAuthorizedFetch] puts the read token on each request. */
sealed interface ReadTokenPlacement {
    /**
     * An `Authorization: Bearer <token>` header, for a host whose HTTP client sends headers. A browser
     * sends it cross-origin only after a CORS preflight the content server allows.
     */
    data object AuthorizationHeader : ReadTokenPlacement

    /**
     * A query parameter named [name], added to each URL with the token percent-encoded, for a host that
     * loads through a URL alone. The token then travels in the URL, which servers, proxies and browser
     * history may record, so prefer [AuthorizationHeader] where the host can send it.
     *
     * @property name The parameter's name, not empty; RFC 6750's `access_token` by default.
     */
    data class QueryParameter(val name: String = DEFAULT_NAME) : ReadTokenPlacement {
        init {
            require(name.isNotEmpty()) { "A query parameter name must not be empty" }
        }

        /** The default parameter name. */
        companion object {
            /** RFC 6750's name for a bearer token in a URL query. */
            const val DEFAULT_NAME: String = "access_token"
        }
    }
}

/** [url] with `name=value` added to its query, before any fragment, both percent-encoded. */
internal fun withQueryParameter(url: String, name: String, value: String): String {
    val fragmentStart = url.indexOf('#').takeIf { it >= 0 } ?: url.length
    val base = url.substring(0, fragmentStart)
    val separator = when {
        '?' !in base -> "?"
        base.endsWith('?') || base.endsWith('&') -> ""
        else -> "&"
    }
    return base + separator + encodeQueryComponent(name) + "=" + encodeQueryComponent(value) + url.substring(fragmentStart)
}

/** [text]'s UTF-8 bytes with everything outside RFC 3986's unreserved characters percent-encoded. */
internal fun encodeQueryComponent(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val char = (byte.toInt() and BYTE_MASK).toChar()
        if (char.isUnreserved()) {
            append(char)
        } else {
            append('%').append(HEX_DIGITS[(byte.toInt() shr NIBBLE_BITS) and NIBBLE_MASK]).append(HEX_DIGITS[byte.toInt() and NIBBLE_MASK])
        }
    }
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '.' || this == '_' || this == '~'

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val NIBBLE_MASK = 0x0F
private const val NIBBLE_BITS = 4
