/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [BrowserTransportOptions] validation and [FetchContentTransport]'s bounded retry,
 * backoff, `Retry-After`, and cancellation behavior across all targets.
 */
class BrowserTransportResilienceTest {
    private val payload = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun optionsExposeExpectedDefaults() {
        val options = BrowserTransportOptions()

        assertEquals(20_000L, options.connectTimeoutMs)
        assertEquals(25_000L, options.stallTimeoutMs)
        assertEquals(120_000L, options.totalTimeoutMs)
        assertEquals(3, options.maxAttempts)
        assertEquals(600L, options.retryBaseDelayMs)
        assertNull(options.onChunk)
    }

    @Test
    fun optionsRejectInvalidValues() {
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(connectTimeoutMs = 0L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(connectTimeoutMs = -1L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(stallTimeoutMs = 0L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(stallTimeoutMs = -5L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(totalTimeoutMs = 0L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(totalTimeoutMs = -10L) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(maxAttempts = -2) }
        assertFailsWith<IllegalArgumentException> { BrowserTransportOptions(retryBaseDelayMs = -1L) }

        // Boundary valid values are accepted.
        val minimal = BrowserTransportOptions(
            connectTimeoutMs = 1L,
            stallTimeoutMs = 1L,
            totalTimeoutMs = 1L,
            maxAttempts = 1,
            retryBaseDelayMs = 0L,
        )
        assertEquals(1, minimal.maxAttempts)
        assertEquals(0L, minimal.retryBaseDelayMs)
    }

    @Test
    fun transientFailuresRetryWithExponentialBackoffUntilSuccess() = runTest {
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = "https://cdn.example.com/harbor-town/",
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 600L),
            delay = { waits += it },
        ) {
            calls++
            when (calls) {
                1 -> FetchOutcome.Failed("connect timeout after 20000ms")
                2 -> FetchOutcome.Failed("stall timeout after 25000ms")
                else -> FetchOutcome.Answered(200, emptyList(), payload)
            }
        }

        val response = transport.send(ContentRequest("bundles/terrain.awkb"))

        assertEquals(200, response.status)
        assertContentEquals(payload, response.body)
        assertEquals(3, calls)
        assertEquals(listOf(600L, 1_200L), waits)
    }

    @Test
    fun nonRetryableFailureFailsImmediatelyWithoutWaiting() = runTest {
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = "not-a-url",
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 600L),
            delay = { waits += it },
        ) {
            calls++
            FetchOutcome.Failed("Invalid URL", retryable = false)
        }

        val failure = assertFailsWith<IllegalStateException> {
            transport.send(ContentRequest("bundles/terrain.awkb"))
        }

        assertEquals(1, calls)
        assertTrue(waits.isEmpty())
        assertTrue("Invalid URL" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun retryableHttpStatusesRetryAndRecover() = runTest {
        for (status in listOf(408, 429, 500, 502, 503, 504)) {
            var calls = 0
            val waits = mutableListOf<Long>()
            val transport = FetchContentTransport(
                baseUrl = null,
                options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 500L),
                delay = { waits += it },
            ) {
                calls++
                if (calls == 1) {
                    FetchOutcome.Answered(status, emptyList(), ByteArray(0))
                } else {
                    FetchOutcome.Answered(200, emptyList(), payload)
                }
            }

            val response = transport.send(ContentRequest("assets/crate.glb"))

            assertEquals(200, response.status, "HTTP $status should retry")
            assertContentEquals(payload, response.body)
            assertEquals(2, calls, "HTTP $status should take two calls")
            assertEquals(listOf(500L), waits, "HTTP $status should back off")
        }
    }

    @Test
    fun retryAfterSecondsIsHonoredOnRetryableStatuses() = runTest {
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 600L, totalTimeoutMs = 10_000L),
            delay = { waits += it },
        ) {
            calls++
            when (calls) {
                1 -> FetchOutcome.Answered(429, listOf("retry-after", "2"), ByteArray(0))
                2 -> FetchOutcome.Answered(503, listOf("retry-after", "0"), ByteArray(0))
                else -> FetchOutcome.Answered(200, emptyList(), payload)
            }
        }

        val response = transport.send(ContentRequest("assets/crate.glb"))

        assertEquals(200, response.status)
        assertEquals(3, calls)
        assertEquals(listOf(2_000L), waits)
    }

    @Test
    fun retryAfterBeyondTotalTimeoutStopsRetryingImmediately() = runTest {
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 600L, totalTimeoutMs = 10_000L),
            delay = { waits += it },
        ) {
            calls++
            FetchOutcome.Answered(503, listOf("retry-after", "3600"), "maintenance".encodeToByteArray())
        }

        val response = transport.send(ContentRequest("assets/crate.glb"))

        assertEquals(503, response.status)
        assertEquals("maintenance", response.body.decodeToString())
        assertEquals(1, calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun nonNumericRetryAfterFallsBackToExponentialBackoff() = runTest {
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 2, retryBaseDelayMs = 400L),
            delay = { waits += it },
        ) {
            calls++
            if (calls == 1) {
                FetchOutcome.Answered(429, listOf("retry-after", "soon"), ByteArray(0))
            } else {
                FetchOutcome.Answered(200, emptyList(), payload)
            }
        }

        val response = transport.send(ContentRequest("assets/crate.glb"))

        assertEquals(200, response.status)
        assertEquals(2, calls)
        assertEquals(listOf(400L), waits)
    }

    @Test
    fun nonRetryableClientStatusesDoNotRetry() = runTest {
        for (status in listOf(400, 401, 403, 404, 410, 422)) {
            var calls = 0
            val waits = mutableListOf<Long>()
            val transport = FetchContentTransport(
                baseUrl = null,
                options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 600L),
                delay = { waits += it },
            ) {
                calls++
                FetchOutcome.Answered(status, emptyList(), ByteArray(0))
            }

            val response = transport.send(ContentRequest("assets/missing.glb"))

            assertEquals(status, response.status)
            assertEquals(1, calls, "HTTP $status must not retry")
            assertTrue(waits.isEmpty(), "HTTP $status must not delay")
        }
    }

    @Test
    fun maxAttemptsExhaustionThrowsOnFailuresAndReturnsFinalStatusOnHttpErrors() = runTest {
        var failedCalls = 0
        val failedWaits = mutableListOf<Long>()
        val failingTransport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 250L),
            delay = { failedWaits += it },
        ) {
            failedCalls++
            FetchOutcome.Failed("network down #$failedCalls")
        }

        val failure = assertFailsWith<IllegalStateException> {
            failingTransport.send(ContentRequest("assets/crate.glb"))
        }
        assertEquals(3, failedCalls)
        assertEquals(listOf(250L, 500L), failedWaits)
        assertTrue("network down #3" in failure.message.orEmpty(), failure.message)

        var httpCalls = 0
        val httpWaits = mutableListOf<Long>()
        val busyTransport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 4, retryBaseDelayMs = 100L),
            delay = { httpWaits += it },
        ) {
            httpCalls++
            FetchOutcome.Answered(502, emptyList(), "gateway #$httpCalls".encodeToByteArray())
        }

        val response = busyTransport.send(ContentRequest("assets/crate.glb"))
        assertEquals(502, response.status)
        assertEquals("gateway #4", response.body.decodeToString())
        assertEquals(4, httpCalls)
        assertEquals(listOf(100L, 200L, 400L), httpWaits)
    }

    @Test
    fun maxAttemptsOneDisablesAllTransportRetries() = runTest {
        var failedCalls = 0
        val failing = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 1),
            delay = { error("must not delay") },
        ) {
            failedCalls++
            FetchOutcome.Failed("offline")
        }
        assertFailsWith<IllegalStateException> { failing.send(ContentRequest("a.glb")) }
        assertEquals(1, failedCalls)

        var statusCalls = 0
        val status503 = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 1),
            delay = { error("must not delay") },
        ) {
            statusCalls++
            FetchOutcome.Answered(503, emptyList(), ByteArray(0))
        }
        assertEquals(503, status503.send(ContentRequest("a.glb")).status)
        assertEquals(1, statusCalls)
    }

    @Test
    fun cancellationDuringFetchAbortsImmediatelyWithoutRetrying() = runTest {
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val waits = mutableListOf<Long>()
        val transport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 100L),
            delay = { waits += it },
        ) {
            calls++
            started.complete(Unit)
            CompletableDeferred<FetchOutcome>().await()
        }

        var caughtCancellation = false
        val job = launch {
            try {
                transport.send(ContentRequest("assets/large.awkb"))
            } catch (_: CancellationException) {
                caughtCancellation = true
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertTrue(caughtCancellation)
        assertEquals(1, calls)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun cancellationDuringBackoffDelayPreventsSubsequentAttempts() = runTest {
        val waiting = CompletableDeferred<Unit>()
        var calls = 0
        val transport = FetchContentTransport(
            baseUrl = null,
            options = BrowserTransportOptions(maxAttempts = 3, retryBaseDelayMs = 1_000L),
            delay = {
                waiting.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
        ) {
            calls++
            FetchOutcome.Failed("transient failure")
        }

        var caughtCancellation = false
        val job = launch {
            try {
                transport.send(ContentRequest("assets/large.awkb"))
            } catch (_: CancellationException) {
                caughtCancellation = true
            }
        }
        waiting.await()
        job.cancel()
        job.join()

        assertTrue(caughtCancellation)
        assertEquals(1, calls)
    }

    @Test
    fun backoffHelperHandlesZeroAndLargeAttemptsWithoutOverflow() {
        assertEquals(0L, retryBackoffDelayMs(1, 0L))
        assertEquals(600L, retryBackoffDelayMs(1, 600L))
        assertEquals(1_200L, retryBackoffDelayMs(2, 600L))
        assertEquals(2_400L, retryBackoffDelayMs(3, 600L))
        assertEquals(30_000L, retryBackoffDelayMs(10, 600L))
        assertEquals(60_000L, retryBackoffDelayMs(5, 60_000L))
        assertEquals(Long.MAX_VALUE, retryBackoffDelayMs(30, Long.MAX_VALUE))
        assertEquals(4_000L, parseRetryAfterSecondsMs(" 4 "))
        assertEquals(0L, parseRetryAfterSecondsMs("0"))
        assertEquals(Long.MAX_VALUE, parseRetryAfterSecondsMs("999999999999999999999"))
        assertNull(parseRetryAfterSecondsMs(""))
        assertNull(parseRetryAfterSecondsMs("-5"))
        assertNull(parseRetryAfterSecondsMs("Sun, 06 Nov 1994 08:49:42 GMT"))
    }
}
