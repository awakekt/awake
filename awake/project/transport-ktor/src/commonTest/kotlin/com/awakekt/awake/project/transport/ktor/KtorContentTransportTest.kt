/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.ContentBundleWriter
import com.awakekt.awake.project.ContentFetchException
import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.project.RateLimitBackoff
import com.awakekt.awake.project.ReadToken
import com.awakekt.awake.project.ReadTokenPlacement
import com.awakekt.awake.project.TokenAuthorizedFetch
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The Ktor transport puts each header where it belongs, answers every status, and lets a 429, or a 503 with Retry-After, back off. */
class KtorContentTransportTest {
    private val base = "https://content.example.com/release/"
    private val model = ByteArray(64) { it.toByte() }

    @Test
    fun theTokenTravelsInTheAuthorizationHeaderOfAGetOfTheResolvedUrl() = runTest {
        val server = Server { respond(model, HttpStatusCode.OK) }

        assertContentEquals(model, fetchThrough(server).fetch("files/assets/crate.glb"))

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://content.example.com/release/files/assets/crate.glb", request.url.toString())
        assertEquals("Bearer t0ken", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun aQueryTokenArrivesWithItsPercentEncodingAndNoHeader() = runTest {
        val server = Server { respond(model, HttpStatusCode.OK) }
        val fetch = TokenAuthorizedFetch(KtorContentTransport(HttpClient(server.engine), base), ReadTokenPlacement.QueryParameter()) {
            ReadToken("a+b/c=", Duration.INFINITE)
        }

        fetch.fetch("files/assets/crate.glb?v=2")

        val request = server.requests.single()
        assertEquals("https://content.example.com/release/files/assets/crate.glb?v=2&access_token=a%2Bb%2Fc%3D", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun aBundleMemberReadAloneSendsARangeHeaderBesideTheToken() = runTest {
        val files = mapOf("assets/crate.glb" to model, "assets/buoy.glb" to ByteArray(40) { 7 })
        val loose = ProjectIndex(
            files = files.map { (path, bytes) -> ProjectIndexEntry(path, bytes.size.toLong(), Sha256.digestHex(bytes), "files/$path") },
        )
        val bundles = mutableMapOf<String, ByteArray>()
        val index = ContentBundleWriter().write(loose, { files.getValue(it.path) }) { bundles[it.url] = it.bytes }
        val indexJson = Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()
        val server = Server { request ->
            val path = request.url.toString().removePrefix(base)
            val range = request.headers[HttpHeaders.Range]
            when {
                path == IndexedAssetSource.DEFAULT_INDEX_PATH -> respond(indexJson, HttpStatusCode.OK)
                range != null -> {
                    val (start, end) = range.removePrefix("bytes=").split('-').map(String::toInt)
                    respond(bundles.getValue(path).copyOfRange(start, end + 1), HttpStatusCode.PartialContent)
                }
                else -> respond(ByteArray(0), HttpStatusCode.NotFound)
            }
        }

        val source = IndexedAssetSource.open(fetch = fetchThrough(server), rangeReads = true)

        assertContentEquals(model, source.read(AssetPath("assets/crate.glb")).getOrThrow())
        val member = server.requests.last()
        assertTrue(member.url.toString().removePrefix(base) in bundles, member.url.toString())
        assertTrue(member.headers[HttpHeaders.Range]!!.startsWith("bytes="))
        assertTrue(server.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer t0ken" })
    }

    @Test
    fun everyStatusComesBackEvenFromAClientThatExpectsSuccess() = runTest {
        val server = Server { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "missing.glb" -> respond("gone".encodeToByteArray(), HttpStatusCode.NotFound)
                else -> respond(ByteArray(0), HttpStatusCode.InternalServerError)
            }
        }
        val client = HttpClient(server.engine) { expectSuccess = true }

        val answered = KtorContentTransport(client, base).send(ContentRequest("files/broken.glb"))
        assertEquals(500, answered.status)

        val failure = assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(KtorContentTransport(client, base)) { ReadToken("t0ken", Duration.INFINITE) }.fetch("files/missing.glb")
        }
        assertEquals(404, failure.status)
    }

    @Test
    fun a401RefreshesTheTokenAndSendsAgain() = runTest {
        val server = Server { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") respond(model, HttpStatusCode.OK) else respond(ByteArray(0), HttpStatusCode.Unauthorized)
        }
        var issued = 0
        val fetch = TokenAuthorizedFetch(KtorContentTransport(HttpClient(server.engine), base)) { ReadToken("token-${++issued}", Duration.INFINITE) }

        assertContentEquals(model, fetch.fetch("files/assets/crate.glb"))
        assertEquals(listOf("Bearer token-1", "Bearer token-2"), server.requests.map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun a429WaitsAsRetryAfterSaysInSecondsThenSucceeds() = runTest {
        val server = Server(rateLimited(times = 1, retryAfter = "2"))
        val waits = mutableListOf<Duration>()

        assertContentEquals(model, fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb"))

        assertEquals(listOf(2.seconds), waits)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun a429WaitsAsAnHttpDateRetryAfterSays() = runTest {
        val server = Server(rateLimited(times = 1, retryAfter = "Wed, 21 Oct 2026 07:28:07 GMT"))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb")

        assertEquals(listOf(7.seconds), waits)
    }

    @Test
    fun a429WithoutRetryAfterBacksOffAndStopsAtTheAttemptLimit() = runTest {
        val server = Server(rateLimited(times = 10, retryAfter = null))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb") }

        assertEquals(429, failure.status)
        assertEquals(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS, server.requests.size)
        assertEquals(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS - 1, waits.size)
        assertTrue(waits.zipWithNext().all { (first, next) -> next >= first / 2 }, "$waits")
    }

    @Test
    fun a503WaitsAsRetryAfterSaysInSecondsThenSucceeds() = runTest {
        val server = Server(rateLimited(times = 1, retryAfter = "2", status = HttpStatusCode.ServiceUnavailable))
        val waits = mutableListOf<Duration>()

        assertContentEquals(model, fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb"))

        assertEquals(listOf(2.seconds), waits)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun a503WaitsAsAnHttpDateRetryAfterSays() = runTest {
        val server = Server(rateLimited(times = 1, retryAfter = "Wed, 21 Oct 2026 07:28:07 GMT", status = HttpStatusCode.ServiceUnavailable))
        val waits = mutableListOf<Duration>()

        fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb")

        assertEquals(listOf(7.seconds), waits)
    }

    @Test
    fun a503RetryAfterBeyondTheBudgetFailsAtOnceWith503() = runTest {
        val server = Server(rateLimited(times = 1, retryAfter = "3600", status = HttpStatusCode.ServiceUnavailable))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb") }

        assertEquals(503, failure.status)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits)
        assertTrue("HTTP 503 Service Unavailable" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun a503WithRetryAfterStopsAtTheAttemptLimit() = runTest {
        val server = Server(rateLimited(times = 10, retryAfter = "1", status = HttpStatusCode.ServiceUnavailable))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb") }

        assertEquals(503, failure.status)
        assertEquals(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS, server.requests.size)
        assertEquals(List(RateLimitBackoff.DEFAULT_MAX_ATTEMPTS - 1) { 1.seconds }, waits)
    }

    @Test
    fun a503WithoutRetryAfterFailsAtOnceAsBefore() = runTest {
        val server = Server(rateLimited(times = 10, retryAfter = null, status = HttpStatusCode.ServiceUnavailable))
        val waits = mutableListOf<Duration>()

        val failure = assertFailsWith<ContentFetchException> { fetchThrough(server, backoff(waits)).fetch("files/assets/crate.glb") }

        assertEquals(503, failure.status)
        assertEquals("files/assets/crate.glb answered HTTP 503", failure.message)
        assertEquals(1, server.requests.size)
        assertEquals(emptyList(), waits)
    }

    @Test
    fun responseHeadersComeBackWithRepeatsJoined() = runTest {
        val server = Server {
            respond(model, HttpStatusCode.OK, headersOf("Vary" to listOf("Accept", "Authorization"), "Retry-After" to listOf("5")))
        }

        val response = KtorContentTransport(HttpClient(server.engine), base).send(ContentRequest("files/a.glb", mapOf("Range" to "bytes=0-9")))

        assertEquals("Accept, Authorization", response.header("vary"))
        assertEquals("5", response.header("retry-after"))
        assertEquals("bytes=0-9", server.requests.single().headers[HttpHeaders.Range])
    }

    @Test
    fun aNetworkFailureIsReportedWithNoStatusAndNoToken() = runTest {
        val server = Server { request -> error("connection refused for ${request.url}") }
        val fetch = TokenAuthorizedFetch(KtorContentTransport(HttpClient(server.engine), base), ReadTokenPlacement.QueryParameter()) {
            ReadToken("s3cret", Duration.INFINITE)
        }

        val failure = assertFailsWith<ContentFetchException> { fetch.fetch("files/assets/crate.glb") }

        assertNull(failure.status)
        assertTrue("connection refused" in failure.message.orEmpty(), failure.message)
        assertFalse("s3cret" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aRelativeBaseUrlIsRejected() {
        assertFailsWith<IllegalArgumentException> { KtorContentTransport(HttpClient(MockEngine { respond(ByteArray(0)) }), "release/") }
    }

    private fun rateLimited(
        times: Int,
        retryAfter: String?,
        status: HttpStatusCode = HttpStatusCode.TooManyRequests,
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData {
        var answered = 0
        return {
            if (answered++ < times) {
                respond(ByteArray(0), status, retryAfter?.let { headersOf(HttpHeaders.RetryAfter, it) } ?: headersOf())
            } else {
                respond(model, HttpStatusCode.OK)
            }
        }
    }

    private fun backoff(waits: MutableList<Duration>) = RateLimitBackoff(
        // 2026-10-21T07:28:00Z
        clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(1_792_567_680)
        },
        delay = { waits += it },
    )

    private fun fetchThrough(server: Server, backoff: RateLimitBackoff = RateLimitBackoff()) =
        TokenAuthorizedFetch(KtorContentTransport(HttpClient(server.engine), base), rateLimitBackoff = backoff) {
            ReadToken("t0ken", Duration.INFINITE)
        }

    /** A [MockEngine] that records each request before [answer] answers it. */
    private class Server(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            answer(request)
        }
    }
}
