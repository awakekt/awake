/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentFetchException
import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.RateLimitBackoff
import com.awakekt.awake.project.ReadToken
import com.awakekt.awake.project.ReadTokenPlacement
import com.awakekt.awake.project.TokenAuthorizedFetch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The browser transport's mapping, run on every target: what `fetch()` is asked to send, and what
 * its answer, or its failure, becomes. The wasm file adds only the `fetch()` call.
 */
class FetchContentTransportTest {
    private val base = "https://content.example.com/release/"
    private val model = ByteArray(64) { it.toByte() }

    @Test
    fun theTokenTravelsInTheAuthorizationHeaderAndTheUrlIsLeftToTheBrowser() = runTest {
        val browser = FakeBrowser { FetchOutcome.Answered(200, emptyList(), model) }

        assertContentEquals(model, fetchThrough(browser).fetch("files/assets/crate.glb"))

        val call = browser.calls.single()
        assertEquals("files/assets/crate.glb", call.url, "resolved by the browser, against baseUrl")
        assertEquals(base, call.baseUrl)
        assertEquals(listOf("Authorization"), call.headerNames)
        assertEquals(listOf("Bearer t0ken"), call.headerValues)
    }

    @Test
    fun aBundleMemberReadAloneSendsRangeBesideTheToken() = runTest {
        val browser = FakeBrowser { FetchOutcome.Answered(206, emptyList(), model.copyOfRange(16, 32)) }

        val bytes = fetchThrough(browser).fetchRange("bundles/a.awkb", 16, 16)

        assertContentEquals(model.copyOfRange(16, 32), bytes)
        val call = browser.calls.single()
        assertEquals(mapOf("Range" to "bytes=16-31", "Authorization" to "Bearer t0ken"), call.headerNames.zip(call.headerValues).toMap())
    }

    @Test
    fun aQueryTokenStaysInTheUrlWithNoHeaders() = runTest {
        val browser = FakeBrowser { FetchOutcome.Answered(200, emptyList(), model) }
        val fetch = TokenAuthorizedFetch(FetchContentTransport(base, browser::fetch), ReadTokenPlacement.QueryParameter()) {
            ReadToken("a+b", Duration.INFINITE)
        }

        fetch.fetch("files/assets/crate.glb")

        val call = browser.calls.single()
        assertEquals("files/assets/crate.glb?access_token=a%2Bb", call.url)
        assertTrue(call.headerNames.isEmpty())
    }

    @Test
    fun everyStatusComesBackAsAResponse() = runTest {
        val transport = FetchContentTransport(null) { FetchOutcome.Answered(503, listOf("content-type", "text/plain"), "busy".encodeToByteArray()) }

        val response = transport.send(ContentRequest("a.glb"))

        assertEquals(503, response.status)
        assertEquals("busy", response.body.decodeToString())
        assertEquals("text/plain", response.header("Content-Type"))
    }

    @Test
    fun aMissingFileFailsWithItsStatus() = runTest {
        val failure = assertFailsWith<ContentFetchException> {
            fetchThrough(FakeBrowser { FetchOutcome.Answered(404, emptyList(), ByteArray(0)) }).fetch("files/missing.glb")
        }
        assertEquals(404, failure.status)
    }

    @Test
    fun a401RefreshesTheTokenAndSendsAgain() = runTest {
        val browser = FakeBrowser { call ->
            if (call.headerValues.single() == "Bearer token-2") FetchOutcome.Answered(200, emptyList(), model) else FetchOutcome.Answered(401, emptyList(), ByteArray(0))
        }
        var issued = 0
        val fetch = TokenAuthorizedFetch(FetchContentTransport(base, browser::fetch)) { ReadToken("token-${++issued}", Duration.INFINITE) }

        assertContentEquals(model, fetch.fetch("files/assets/crate.glb"))
        assertEquals(listOf("Bearer token-1", "Bearer token-2"), browser.calls.map { it.headerValues.single() })
    }

    @Test
    fun anExposedRetryAfterIsWaitedOut() = runTest {
        var answered = 0
        val browser = FakeBrowser {
            // A browser lists names in lower case.
            if (answered++ == 0) FetchOutcome.Answered(429, listOf("retry-after", "4"), ByteArray(0)) else FetchOutcome.Answered(200, emptyList(), model)
        }
        val waits = mutableListOf<Duration>()

        assertContentEquals(model, fetchThrough(browser, RateLimitBackoff(delay = { waits += it })).fetch("files/assets/crate.glb"))

        assertEquals(listOf(4.seconds), waits)
        assertEquals(2, browser.calls.size)
    }

    @Test
    fun aRetryAfterTheServerDoesNotExposeBacksOffExponentiallyAndStops() = runTest {
        val browser = FakeBrowser { FetchOutcome.Answered(429, listOf("content-length", "0"), ByteArray(0)) }
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> {
            fetchThrough(browser, RateLimitBackoff(delay = { waits += it })).fetch("files/assets/crate.glb")
        }

        assertEquals(429, failure.status)
        assertEquals(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS, browser.calls.size)
        assertEquals(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS - 1, waits.size)
        assertTrue(waits.all { it <= RateLimitBackoff.DEFAULT_MAX_DELAY }, "$waits")
    }

    @Test
    fun aRejectedFetchNamesWhatACrossOriginServerMustAllowAndNeverTheToken() = runTest {
        val browser = FakeBrowser { FetchOutcome.Failed("Failed to fetch") }
        val fetch = TokenAuthorizedFetch(FetchContentTransport(base, browser::fetch)) { ReadToken("s3cret", Duration.INFINITE) }

        val failure = assertFailsWith<ContentFetchException> { fetch.fetch("files/assets/crate.glb") }

        assertNull(failure.status)
        val message = failure.message.orEmpty()
        assertTrue("files/assets/crate.glb: Failed to fetch" in message, message)
        assertTrue("Access-Control-Allow-Origin" in message && "the Authorization request header" in message, message)
        assertFalse("s3cret" in message, message)
    }

    @Test
    fun aFailureMessageLeavesTheQueryOut() {
        val failure = fetchFailure(ContentRequest("files/a.glb?access_token=s3cret", mapOf("Range" to "bytes=0-1")), "Failed to fetch")

        assertFalse("s3cret" in failure.message.orEmpty(), failure.message)
        assertTrue("the Range request header" in failure.message.orEmpty(), failure.message)
        assertEquals(
            listOf("Authorization", "Range"),
            preflightHeaders(mapOf("Accept" to "*/*", "Authorization" to "Bearer t", "Range" to "bytes=0-1", "Content-Language" to "en")),
        )
        assertTrue(fetchFailure(ContentRequest("a.glb"), "x").message.orEmpty().endsWith("(Access-Control-Allow-Origin)"))
    }

    @Test
    fun anOpaqueResponseIsAFailureNotAStatus() = runTest {
        val transport = FetchContentTransport(null) { FetchOutcome.Answered(0, emptyList(), ByteArray(0)) }

        val failure = assertFailsWith<IllegalStateException> { transport.send(ContentRequest("a.glb")) }

        assertTrue("opaque" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun headersMustComeInPairs() = runTest {
        val transport = FetchContentTransport(null) { FetchOutcome.Answered(200, listOf("retry-after"), ByteArray(0)) }

        assertFailsWith<IllegalArgumentException> { transport.send(ContentRequest("a.glb")) }
    }

    private fun fetchThrough(browser: FakeBrowser, backoff: RateLimitBackoff = RateLimitBackoff()) =
        TokenAuthorizedFetch(FetchContentTransport(base, browser::fetch), rateLimitBackoff = backoff) { ReadToken("t0ken", Duration.INFINITE) }

    /** Stands in for `fetch()`: records each call and answers it with [answer]. */
    private class FakeBrowser(private val answer: (FetchCall) -> FetchOutcome) {
        val calls = mutableListOf<FetchCall>()

        suspend fun fetch(call: FetchCall): FetchOutcome {
            calls += call
            return answer(call)
        }
    }
}
