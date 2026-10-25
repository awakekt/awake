/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Protected content reads with a short-lived token attached, refreshed and retried, and never leaked. */
@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class TokenAuthorizedFetchTest {
    private val scene = "{\"version\": 1}".encodeToByteArray()
    private val model = ByteArray(64) { it.toByte() }
    private val texture = ByteArray(48) { (it * 3).toByte() }
    private val sound = ByteArray(32) { (it * 5).toByte() }
    private val index = ProjectIndex(
        files = listOf(
            entry("scenes/main.scene.json", scene),
            entry("assets/crate.glb", model),
            entry("assets/crate.png", texture),
            entry("assets/crate.wav", sound),
        ),
    )
    private val allPaths = index.files.map { it.path }

    @Test
    fun theTokenIsAttachedToTheIndexAndEveryFileRequest() = runTest {
        val server = ContentServer(accepting = setOf("token-1"))
        val authorized = TokenAuthorizedFetch(server, tokenProvider = Tokens())

        val source = IndexedAssetSource.open(fetch = authorized)
        source.read(AssetPath("assets/crate.glb")).getOrThrow()
        source.read(AssetPath("scenes/main.scene.json")).getOrThrow()

        assertEquals(
            listOf(IndexedAssetSource.DEFAULT_INDEX_PATH, "files/assets/crate.glb", "files/scenes/main.scene.json"),
            server.requests.map { it.url },
            "the index is read through the same fetch as the files",
        )
        assertTrue(server.requests.all { it.headers == mapOf("Authorization" to "Bearer token-1") }, "${server.requests}")
    }

    @Test
    fun aQueryParameterCarriesTheTokenForAHostWithoutHeaders() = runTest {
        val server = ContentServer(accepting = setOf("token-1"))
        val authorized = TokenAuthorizedFetch(server, ReadTokenPlacement.QueryParameter(), tokenProvider = Tokens())

        assertContentEquals(model, authorized.fetch("files/assets/crate.glb"))

        val request = server.requests.single()
        assertEquals("files/assets/crate.glb?access_token=token-1", request.url)
        assertTrue(request.headers.isEmpty(), "no header carries the token: ${request.headers}")
    }

    @Test
    fun aQueryTokenKeepsTheUrlsOwnQueryAndFragmentAndIsPercentEncoded() {
        assertEquals("a.glb?v=2&access_token=t", withQueryParameter("a.glb?v=2", "access_token", "t"))
        assertEquals("a.glb?access_token=t#part", withQueryParameter("a.glb#part", "access_token", "t"))
        assertEquals("a.glb?v=2&key=t#part", withQueryParameter("a.glb?v=2#part", "key", "t"))
        assertEquals("a.glb?key=t", withQueryParameter("a.glb?", "key", "t"))
        assertEquals("a.glb?k%20y=a%2Bb%2Fc%3D%C3%A9-._~", withQueryParameter("a.glb", "k y", "a+b/c=é-._~"))
    }

    @Test
    fun theTokenRefreshesShortlyBeforeItExpires() = runTest {
        val clock = TestTimeSource()
        val server = ContentServer(accepting = setOf("token-1", "token-2"))
        val tokens = Tokens(lifetime = 10.minutes)
        val authorized = TokenAuthorizedFetch(server, refreshAhead = 1.minutes, timeSource = clock, tokenProvider = tokens)

        authorized.fetch("files/assets/crate.glb")
        clock += 8.minutes
        authorized.fetch("files/assets/crate.glb")
        assertEquals(1, tokens.calls, "a token with two minutes left is still used")

        clock += 90.seconds
        authorized.fetch("files/assets/crate.glb")

        assertEquals(2, tokens.calls, "a token within a minute of expiring is replaced before the request")
        assertEquals(listOf("token-1", "token-1", "token-2"), server.requests.map { tokenOf(it) })
    }

    @Test
    fun aTokenShorterLivedThanTwiceRefreshAheadIsReplacedAtHalfItsLifetime() = runTest {
        val clock = TestTimeSource()
        val tokens = Tokens(lifetime = 60.seconds)
        val authorized = TokenAuthorizedFetch(ContentServer(accepting = setOf("token-1", "token-2")), refreshAhead = 60.seconds, timeSource = clock, tokenProvider = tokens)

        authorized.fetch("files/assets/crate.glb")
        clock += 29.seconds
        authorized.fetch("files/assets/crate.glb")
        assertEquals(1, tokens.calls, "not refreshed on every request")

        clock += 2.seconds
        authorized.fetch("files/assets/crate.glb")
        assertEquals(2, tokens.calls)
    }

    @Test
    fun a401RefreshesOnceAndRetriesTheRequest() = runTest {
        // The first token was revoked server-side before it expired.
        val server = ContentServer(accepting = setOf("token-2"))
        val tokens = Tokens()
        val authorized = TokenAuthorizedFetch(server, tokenProvider = tokens)

        assertContentEquals(model, authorized.fetch("files/assets/crate.glb"))
        assertEquals(listOf("token-1", "token-2"), server.requests.map { tokenOf(it) })

        authorized.fetch("files/scenes/main.scene.json")
        assertEquals(2, tokens.calls, "the refreshed token serves the next request without another refresh")
        assertEquals("token-2", tokenOf(server.requests.last()))
    }

    @Test
    fun a403AlsoRefreshesOnceAndRetries() = runTest {
        val server = ContentServer(accepting = setOf("token-2"), refusal = 403)

        assertContentEquals(model, TokenAuthorizedFetch(server, tokenProvider = Tokens()).fetch("files/assets/crate.glb"))
        assertEquals(2, server.requests.size)
    }

    @Test
    fun concurrentPrefetchesShareOneRefresh() = runTest {
        val release = CompletableDeferred<Unit>()
        val tokens = Tokens(gate = release)
        val server = ContentServer(accepting = setOf("token-1"))
        val source = IndexedAssetSource(index, TokenAuthorizedFetch(server, tokenProvider = tokens)::fetch)

        val job = launch { source.prefetch(allPaths) }
        advanceUntilIdle()
        assertEquals(1, tokens.calls, "every prefetch waits on the one refresh in flight")
        release.complete(Unit)
        job.join()

        assertEquals(1, tokens.calls)
        assertEquals(allPaths.size, server.requests.size)
        assertTrue(server.requests.all { tokenOf(it) == "token-1" })
    }

    @Test
    fun concurrentRefusalsShareOneRefresh() = runTest {
        val tokens = Tokens()
        val server = ContentServer(accepting = setOf("token-2"), latency = 10.milliseconds)
        val source = IndexedAssetSource(index, TokenAuthorizedFetch(server, tokenProvider = tokens)::fetch)

        source.prefetch(allPaths)

        assertEquals(2, tokens.calls, "four refused requests refresh the token once between them")
        assertEquals(List(allPaths.size) { "token-1" } + List(allPaths.size) { "token-2" }, server.requests.map { tokenOf(it) })
    }

    @Test
    fun aSecondRefusalFailsWithAClearError() = runTest {
        val server = ContentServer(accepting = emptySet())
        val tokens = Tokens()

        val failure = assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(server, tokenProvider = tokens).fetch("files/assets/crate.glb")
        }

        assertEquals(401, failure.status)
        assertEquals("files/assets/crate.glb", failure.url)
        assertTrue(failure.message.orEmpty().contains("files/assets/crate.glb"), "names the file: ${failure.message}")
        assertTrue(failure.message.orEmpty().contains("refreshed"), "says a fresh token was refused too: ${failure.message}")
        assertEquals(2, tokens.calls, "one refresh, not a loop")
        assertEquals(2, server.requests.size, "one retry, not a loop")
    }

    @Test
    fun anotherStatusFailsWithoutARetry() = runTest {
        val server = ContentServer(accepting = setOf("token-1"))
        val tokens = Tokens()

        val failure = assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(server, tokenProvider = tokens).fetch("files/assets/missing.glb")
        }

        assertEquals(404, failure.status)
        assertEquals(1, tokens.calls)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun theTokenNeverAppearsInAnExceptionMessage() = runTest {
        val failures = mutableListOf<Throwable>()
        val query = ReadTokenPlacement.QueryParameter()
        // Refused twice, and a status other than success.
        failures += assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(ContentServer(accepting = emptySet()), query, tokenProvider = Tokens()).fetch("files/assets/crate.glb")
        }
        failures += assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(ContentServer(accepting = setOf("token-1")), query, tokenProvider = Tokens()).fetch("files/missing.glb")
        }
        // A host client whose failure quotes the URL it sent, query token included, and the header.
        val quoting = ContentTransport { request ->
            throw IllegalStateException("GET ${request.url} timed out", IllegalArgumentException("sent ${request.headers}"))
        }
        val quoted = assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(quoting, query, tokenProvider = Tokens()).fetch("files/assets/crate.glb")
        }
        assertTrue(quoted.message.orEmpty().contains("<redacted>"), "the failure is still described: ${quoted.message}")
        assertNull(quoted.cause, "a cause that quotes the token isn't attached")
        failures += quoted
        failures += assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch(quoting, tokenProvider = Tokens()).fetch("files/assets/crate.glb")
        }
        // A file the server altered still fails its check, naming the path only.
        val tampered = ContentServer(accepting = setOf("token-1"), replaced = mapOf("files/assets/crate.glb" to model.copyOf().also { it[10] = 99 }))
        failures += IndexedAssetSource(index, TokenAuthorizedFetch(tampered, query, tokenProvider = Tokens())::fetch)
            .read(AssetPath("assets/crate.glb")).exceptionOrNull()!!

        failures.forEach { failure ->
            assertFalse(failure.stackTraceToString().contains("token-1"), "the token leaked into: ${failure.stackTraceToString()}")
        }
    }

    @Test
    fun aTransportFailureThatDoesNotQuoteTheTokenKeepsItsCause() = runTest {
        val offline = IllegalStateException("host unreachable")

        val failure = assertFailsWith<ContentFetchException> {
            TokenAuthorizedFetch({ throw offline }, tokenProvider = Tokens()).fetch("files/assets/crate.glb")
        }

        assertSame(offline, failure.cause)
        assertNull(failure.status)
        assertTrue(failure.message.orEmpty().contains("files/assets/crate.glb"))
    }

    @Test
    fun theTokenNeverReachesTheCache() = runTest {
        val cache = RecordingCache()
        val server = ContentServer(accepting = setOf("token-1"))
        val authorized = TokenAuthorizedFetch(server, ReadTokenPlacement.QueryParameter(), tokenProvider = Tokens())

        IndexedAssetSource.open(fetch = authorized, cache = cache).prefetch(allPaths)

        assertEquals(index.files.map { it.sha256 }.toSet(), cache.stored.keys, "cached by SHA-256 alone")
        index.files.forEach { entry ->
            assertContentEquals(server.files.getValue(entry.url), cache.stored.getValue(entry.sha256), "only the checked bytes are stored")
        }
        assertTrue(cache.stored.keys.none { "token" in it })
    }

    @Test
    fun aNewTokenFindsTheFilesAlreadyCached() = runTest {
        val cache = RecordingCache()
        IndexedAssetSource.open(fetch = TokenAuthorizedFetch(ContentServer(accepting = setOf("token-1")), tokenProvider = Tokens()), cache = cache)
            .prefetch(allPaths)

        // A later session: another token, and the next release lists the same files.
        val nextSession = ContentServer(accepting = setOf("session-2-token-1"))
        val source = IndexedAssetSource.open(fetch = TokenAuthorizedFetch(nextSession, tokenProvider = Tokens(prefix = "session-2-token")), cache = cache)
        source.prefetch(allPaths)

        assertEquals(listOf(IndexedAssetSource.DEFAULT_INDEX_PATH), nextSession.requests.map { it.url }, "only the index is read again")
    }

    @Test
    fun aFailedRefreshIsSharedByTheRequestsWaitingOnIt() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val authorized = TokenAuthorizedFetch(ContentServer(accepting = setOf("token-2"))) {
            calls++
            if (calls == 1) {
                release.await()
                error("sign-in service unavailable")
            }
            ReadToken("token-$calls", 10.minutes)
        }
        val waiting = List(3) { async { runCatching { authorized.fetch("files/assets/crate.glb") }.exceptionOrNull() } }
        advanceUntilIdle()
        release.complete(Unit)

        waiting.forEach { request ->
            val thrown = request.await()
            assertTrue(thrown is ContentFetchException && thrown.message.orEmpty().contains("read token"), "got $thrown")
        }
        assertEquals(1, calls, "the waiting requests share the one failure instead of asking again")
        assertContentEquals(model, authorized.fetch("files/assets/crate.glb"), "the next request asks again")
        assertEquals(2, calls)
    }

    @Test
    fun aCancelledRefreshLeavesTheNextRequestToAskAgain() = runTest {
        var calls = 0
        val authorized = TokenAuthorizedFetch(ContentServer(accepting = setOf("token-2"))) {
            calls++
            if (calls == 1) awaitCancellation()
            ReadToken("token-$calls", 10.minutes)
        }

        val first = launch { authorized.fetch("files/assets/crate.glb") }
        advanceUntilIdle()
        first.cancel()
        first.join()

        assertContentEquals(model, authorized.fetch("files/assets/crate.glb"))
        assertEquals(2, calls)
    }

    @Test
    fun aTokenOrRequestPrintedForALogNeverShowsTheToken() {
        val token = ReadToken("token-1", 5.minutes)
        val header = ContentRequest("files/a.glb", mapOf("Authorization" to "Bearer token-1"))
        val query = ContentRequest("files/a.glb?access_token=token-1")

        listOf(token.toString(), header.toString(), query.toString(), ContentResponse(200, model).toString()).forEach {
            assertFalse(it.contains("token-1"), "printed: $it")
        }
        assertEquals("ContentRequest(url=files/a.glb, headers=[Authorization])", header.toString())
    }

    @Test
    fun aMalformedTokenIsRefusedWithoutEchoingIt() {
        listOf("", "token one", "token-1\r\nX-Injected: 1").forEach { value ->
            val failure = assertFailsWith<IllegalArgumentException> { ReadToken(value, 5.minutes) }
            assertTrue(value.isEmpty() || !failure.message.orEmpty().contains(value), "echoed: ${failure.message}")
        }
        assertFailsWith<IllegalArgumentException> { ReadTokenPlacement.QueryParameter("") }
        assertFailsWith<IllegalArgumentException> { TokenAuthorizedFetch(ContentServer(emptySet()), refreshAhead = (-1).seconds) { ReadToken("t", Duration.ZERO) } }
    }

    private fun entry(path: String, bytes: ByteArray) =
        ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = "files/$path")

    /** Hands out `token-1`, `token-2`, ... and counts the calls; optionally waits on [gate] first. */
    private class Tokens(
        private val lifetime: Duration = 10.minutes,
        private val gate: CompletableDeferred<Unit>? = null,
        private val prefix: String = "token",
    ) : suspend () -> ReadToken {
        var calls = 0
        override suspend fun invoke(): ReadToken {
            calls++
            gate?.await()
            return ReadToken("$prefix-$calls", lifetime)
        }
    }

    /** Serves the index and files to requests carrying a token in [accepting], refusing the rest. */
    private inner class ContentServer(
        private val accepting: Set<String>,
        private val refusal: Int = 401,
        private val latency: Duration = Duration.ZERO,
        replaced: Map<String, ByteArray> = emptyMap(),
    ) : ContentTransport {
        val files: Map<String, ByteArray> =
            index.files.associate { it.url to bytesOf(it.path) } +
                (IndexedAssetSource.DEFAULT_INDEX_PATH to Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()) +
                replaced
        val requests = mutableListOf<ContentRequest>()

        override suspend fun send(request: ContentRequest): ContentResponse {
            requests += request
            if (latency > Duration.ZERO) delay(latency)
            val file = files[request.url.substringBefore('?')]
            return when {
                tokenOf(request) !in accepting -> ContentResponse(refusal, ByteArray(0))
                file == null -> ContentResponse(404, ByteArray(0))
                else -> ContentResponse(200, file)
            }
        }
    }

    private fun bytesOf(path: String): ByteArray = when (path) {
        "scenes/main.scene.json" -> scene
        "assets/crate.glb" -> model
        "assets/crate.png" -> texture
        else -> sound
    }

    private class RecordingCache : ContentCache {
        val stored = mutableMapOf<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }

    private companion object {
        fun tokenOf(request: ContentRequest): String? =
            request.headers["Authorization"]?.removePrefix("Bearer ")
                ?: request.url.substringAfter("access_token=", "").substringBefore('&').ifEmpty { null }
    }
}
