/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

/**
 * A 429, or a 503 with `Retry-After`, is waited out as `Retry-After` says (a 429 without it, with a
 * bounded, jittered exponential delay).
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent, currentTime
class RateLimitBackoffTest {
    private val model = ByteArray(16) { it.toByte() }

    // 1994-11-06T08:49:37Z, RFC 9110's example date.
    private val now = Instant.fromEpochSeconds(784_111_777)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun aRetryAfterInSecondsIsWaitedExactlyBeforeTheRequestIsSentAgain() = runTest {
        val server = RateLimitedServer(listOf(429 to "3", 200 to null))
        val waits = mutableListOf<Duration>()

        val bytes = fetchThrough(server, backoff(waits)).fetch("files/a.glb")

        assertContentEquals(model, bytes)
        assertEquals(listOf(3.seconds), waits)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun aRetryAfterHttpDateIsMeasuredAgainstTheClock() = runTest {
        val server = RateLimitedServer(listOf(429 to "Sun, 06 Nov 1994 08:49:42 GMT", 200 to null))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits)).fetch("files/a.glb")

        assertEquals(listOf(5.seconds), waits)
    }

    @Test
    fun aRetryAfterDateAlreadyPastSendsAgainAtOnce() = runTest {
        val server = RateLimitedServer(listOf(429 to "Sun, 06 Nov 1994 08:00:00 GMT", 200 to null))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits)).fetch("files/a.glb")

        assertEquals(2, server.requests.size)
        assertEquals(emptyList(), waits, "a zero wait doesn't suspend")
    }

    @Test
    fun withoutRetryAfterTheWaitsDoubleWithJitterUpToTheAttemptLimit() = runTest {
        val server = RateLimitedServer(List(4) { 429 to null })
        val waits = mutableListOf<Duration>()
        val backoff = backoff(waits, initialDelay = 1.seconds, maxDelay = 3.seconds, maxTotalWait = 1.minutes)

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff).fetch("files/a.glb") }

        assertEquals(429, failure.status)
        assertEquals(4, server.requests.size, "maxAttempts sends, the first included")
        assertEquals(3, waits.size)
        // Each wait is between half and all of 1 s, 2 s, then 4 s capped at 3 s.
        listOf(1.seconds, 2.seconds, 3.seconds).zip(waits).forEach { (ceiling, wait) ->
            assertTrue(wait in ceiling / 2..ceiling, "$wait is not between ${ceiling / 2} and $ceiling")
        }
        assertTrue("sent 4 times" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aRetryAfterThatDoesNotParseFallsBackToTheExponentialDelay() = runTest {
        val server = RateLimitedServer(listOf(429 to "soon", 200 to null))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits, initialDelay = 2.seconds)).fetch("files/a.glb")

        assertTrue(waits.single() in 1.seconds..2.seconds, "$waits")
    }

    @Test
    fun aWaitLongerThanTheBudgetIsNotStartedAndThe429FailsAtOnce() = runTest {
        val server = RateLimitedServer(listOf(429 to "120", 200 to null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/a.glb") }

        assertEquals(429, failure.status)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits)
        assertTrue("2m" in failure.message.orEmpty() && "30s" in failure.message.orEmpty(), failure.message)
        assertTrue(failure.message.orEmpty().startsWith("files/a.glb was answered HTTP 429"), failure.message)
    }

    @Test
    fun a503RetryAfterInSecondsIsWaitedExactlyBeforeTheRequestIsSentAgain() = runTest {
        val server = RateLimitedServer(listOf(503 to "3", 200 to null))
        val waits = mutableListOf<Duration>()

        val bytes = fetchThrough(server, backoff(waits)).fetch("files/a.glb")

        assertContentEquals(model, bytes)
        assertEquals(listOf(3.seconds), waits)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun a503RetryAfterHttpDateIsMeasuredAgainstTheClock() = runTest {
        val server = RateLimitedServer(listOf(503 to "Sun, 06 Nov 1994 08:49:42 GMT", 200 to null))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits)).fetch("files/a.glb")

        assertEquals(listOf(5.seconds), waits)
    }

    @Test
    fun a503WithoutRetryAfterFailsAtOnceAsItAlwaysDid() = runTest {
        val server = RateLimitedServer(listOf(503 to null, 200 to null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/a.glb") }

        assertEquals(503, failure.status)
        assertEquals("files/a.glb answered HTTP 503", failure.message)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits)
    }

    @Test
    fun a503WithARetryAfterThatDoesNotParseIsNotRetried() = runTest {
        val server = RateLimitedServer(listOf(503 to "soon", 200 to null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/a.glb") }

        assertEquals(503, failure.status)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits, "unlike a 429, there is no exponential fallback")
    }

    @Test
    fun a503RetryAfterLongerThanTheBudgetIsNotStartedAndFailsAtOnceWith503() = runTest {
        val server = RateLimitedServer(listOf(503 to "120", 200 to null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/a.glb") }

        assertEquals(503, failure.status)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits)
        assertTrue("2m" in failure.message.orEmpty() && "30s" in failure.message.orEmpty(), failure.message)
        assertTrue(failure.message.orEmpty().startsWith("files/a.glb was answered HTTP 503 Service Unavailable"), failure.message)
    }

    @Test
    fun a503WithRetryAfterStopsAtTheAttemptLimit() = runTest {
        val server = RateLimitedServer(List(10) { 503 to "1" })
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits, maxAttempts = 3)).fetch("files/a.glb") }

        assertEquals(503, failure.status)
        assertEquals(3, server.requests.size)
        assertEquals(listOf(1.seconds, 1.seconds), waits)
    }

    @Test
    fun a429And503ShareOneAttemptLimitAndOneBudget() = runTest {
        val server = RateLimitedServer(listOf(429 to "2", 503 to "2", 429 to "2", 200 to null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> {
            fetchThrough(server, backoff(waits, maxAttempts = 5, maxTotalWait = 5.seconds)).fetch("files/a.glb")
        }

        assertEquals(429, failure.status)
        assertEquals(listOf(2.seconds, 2.seconds), waits, "2 s more would take the fetch past its 5 s")
        assertEquals(3, server.requests.size)
    }

    @Test
    fun noneReportsTheFirst503WithRetryAfter() = runTest {
        val server = RateLimitedServer(listOf(503 to "1", 200 to null))

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, RateLimitBackoff.None).fetch("files/a.glb") }

        assertEquals(503, failure.status)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun aRangeReadAnswered503WithRetryAfterIsPacedTheSameWay() = runTest {
        val server = RateLimitedServer(listOf(503 to "1", 206 to null))
        val waits = mutableListOf<Duration>()

        val bytes = fetchThrough(server, backoff(waits)).fetchRange("bundles/a.awkb", 0, model.size.toLong())

        assertContentEquals(model, bytes)
        assertEquals(listOf(1.seconds), waits)
    }

    @Test
    fun theBudgetCountsEveryWaitOfOneFetch() = runTest {
        val server = RateLimitedServer(listOf(429 to "3", 429 to "3", 200 to null))
        val waits = mutableListOf<Duration>()

        assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits, maxTotalWait = 5.seconds)).fetch("files/a.glb") }

        assertEquals(listOf(3.seconds), waits, "3 s more would take the fetch past its 5 s")
        assertEquals(2, server.requests.size)
    }

    @Test
    fun noneReportsTheFirst429() = runTest {
        val server = RateLimitedServer(listOf(429 to "1", 200 to null))

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, RateLimitBackoff.None).fetch("files/a.glb") }

        assertEquals(429, failure.status)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun theDefaultWaitSuspendsWithoutBlocking() = runTest {
        val server = RateLimitedServer(listOf(429 to "3", 200 to null))
        val fetch = fetchThrough(server, RateLimitBackoff())

        val pending = async { fetch.fetch("files/a.glb") }
        runCurrent()
        assertEquals(1, server.requests.size)
        advanceTimeBy(2_999.milliseconds)
        assertEquals(1, server.requests.size, "not sent again before Retry-After")

        assertContentEquals(model, pending.await())
        assertEquals(2, server.requests.size)
        assertEquals(3_000, currentTime)
    }

    @Test
    fun aWaitThatOutlivesTheTokenSendsAgainWithAFreshOne() = runTest {
        val time = TestTimeSource()
        val server = RateLimitedServer(listOf(429 to "20", 200 to null))
        var issued = 0
        val backoff = RateLimitBackoff(clock = clock, delay = { time += it })
        val fetch = TokenAuthorizedFetch(server, refreshAhead = 5.seconds, timeSource = time, rateLimitBackoff = backoff) {
            ReadToken("token-${++issued}", 20.seconds)
        }

        fetch.fetch("files/a.glb")

        assertEquals(listOf("Bearer token-1", "Bearer token-2"), server.requests.map { it.headers["Authorization"] })
    }

    @Test
    fun aRangeReadOfABundleMemberIsPacedTheSameWay() = runTest {
        val server = RateLimitedServer(listOf(429 to "1", 206 to null))
        val waits = mutableListOf<Duration>()

        val bytes = fetchThrough(server, backoff(waits)).fetchRange("bundles/a.awkb", 0, model.size.toLong())

        assertContentEquals(model, bytes)
        assertEquals(listOf(1.seconds), waits)
        assertTrue(server.requests.all { it.headers["Range"] == "bytes=0-15" })
    }

    @Test
    fun aRefusedTokenAndA429ShareOneAttemptLimit() = runTest {
        val server = RateLimitedServer(listOf(429 to "1", 401 to null, 429 to "1", 429 to "1"))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> {
            fetchThrough(server, backoff(waits, maxAttempts = 3)).fetch("files/a.glb")
        }

        assertEquals(429, failure.status)
        assertEquals(4, server.requests.size, "three 429s and the one refusal")
        assertEquals(listOf(1.seconds, 1.seconds), waits)
    }

    @Test
    fun retryAfterReadsSecondsAndAllThreeHttpDateForms() {
        assertEquals(0.seconds, retryAfterDelay("0", now))
        assertEquals(120.seconds, retryAfterDelay(" 120 ", now))
        assertEquals(Duration.INFINITE, retryAfterDelay("99999999999999999999999", now))
        assertEquals(10.seconds, retryAfterDelay("Sun, 06 Nov 1994 08:49:47 GMT", now))
        assertEquals(10.seconds, retryAfterDelay("Sunday, 06-Nov-94 08:49:47 GMT", now))
        assertEquals(10.seconds, retryAfterDelay("Sun Nov  6 08:49:47 1994", now))
        assertEquals(Duration.ZERO, retryAfterDelay("Sat, 05 Nov 1994 08:49:37 GMT", now))
        listOf("", "-1", "+5", "1.5", "soon", "Sun, 06 Nov 1994 08:49:47 UTC", "Sun, 31 Nov 1994 08:49:47 GMT", "Sun, 06 Nov 1994 24:00:00 GMT")
            .forEach { assertNull(retryAfterDelay(it, now), "'$it' is no Retry-After") }
    }

    @Test
    fun httpDatesConvertToTheRightInstant() {
        assertEquals(Instant.fromEpochSeconds(0), parseHttpDate("Thu, 01 Jan 1970 00:00:00 GMT"))
        assertEquals(Instant.fromEpochSeconds(951_782_400), parseHttpDate("Tue, 29 Feb 2000 00:00:00 GMT"))
        assertEquals(Instant.fromEpochSeconds(4_107_542_400), parseHttpDate("Fri, 01 Mar 2100 00:00:00 GMT"))
        assertEquals(parseHttpDate("Sat, 01 Jan 2033 00:00:00 GMT"), parseHttpDate("Saturday, 01-Jan-33 00:00:00 GMT"), "a year below 70 is 20xx")
        assertNull(parseHttpDate("Mon, 29 Feb 2100 00:00:00 GMT"), "2100 is no leap year")
        assertNull(parseHttpDate("Funday, 06 Nov 1994 08:49:37 GMT"))
    }

    @Test
    fun aResponseHeaderIsFoundWhateverItsCase() {
        val response = ContentResponse(429, ByteArray(0), mapOf("retry-after" to "5"))
        assertEquals("5", response.header("Retry-After"))
        assertNull(response.header("Content-Range"))
        assertTrue(ContentResponse(200, ByteArray(0)).headers.isEmpty())
    }

    @Test
    fun aPolicyOutsideItsBoundsIsRejected() {
        assertFailsWith<IllegalArgumentException> { RateLimitBackoff(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { RateLimitBackoff(maxTotalWait = (-1).seconds) }
        assertFailsWith<IllegalArgumentException> { RateLimitBackoff(initialDelay = 5.seconds, maxDelay = 1.seconds) }
    }

    private fun backoff(
        waits: MutableList<Duration>,
        maxAttempts: Int = RateLimitBackoff.DEFAULT_MAX_ATTEMPTS,
        maxTotalWait: Duration = RateLimitBackoff.DEFAULT_MAX_TOTAL_WAIT,
        initialDelay: Duration = RateLimitBackoff.DEFAULT_INITIAL_DELAY,
        maxDelay: Duration = maxOf(initialDelay, RateLimitBackoff.DEFAULT_MAX_DELAY),
    ) = RateLimitBackoff(maxAttempts, maxTotalWait, initialDelay, maxDelay, clock = clock, delay = { waits += it })

    private fun fetchThrough(server: ContentTransport, backoff: RateLimitBackoff) =
        TokenAuthorizedFetch(server, rateLimitBackoff = backoff) { ReadToken("t0ken", Duration.INFINITE) }

    /** Answers each request with the next of [answers] (a status and an optional `Retry-After`), then the last. */
    private inner class RateLimitedServer(private val answers: List<Pair<Int, String?>>) : ContentTransport {
        val requests = mutableListOf<ContentRequest>()

        override suspend fun send(request: ContentRequest): ContentResponse {
            requests += request
            val (status, retryAfter) = answers[minOf(requests.size, answers.size) - 1]
            return ContentResponse(
                status,
                if (status in 200..299) model else ByteArray(0),
                retryAfter?.let { mapOf("retry-after" to it) } ?: emptyMap(),
            )
        }
    }
}
