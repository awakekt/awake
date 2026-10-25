/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.ktor

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.ContentCache
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.project.RateLimitBackoff
import com.awakekt.awake.project.ReadToken
import com.awakekt.awake.project.TokenAuthorizedFetch
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The "Protected content" guide includes these samples, so they keep compiling and doing what it says. */
@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class KtorTransportDocsSampleTest {
    private val model = ByteArray(64) { it.toByte() }
    private val index = ProjectIndex(
        files = listOf(
            ProjectIndexEntry("assets/crate.glb", model.size.toLong(), Sha256.digestHex(model), "files/assets/crate.glb"),
        ),
    )
    private val seen = mutableListOf<Pair<String, String?>>()
    private var rateLimited = 0

    /** Stands in for the content server: it wants the player's token, and asks for one pause. */
    private val engine = MockEngine { request ->
        seen += request.url.toString() to request.headers[HttpHeaders.Authorization]
        val body = when (request.url.toString()) {
            "https://content.example.com/release/project.index.json" -> Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()
            "https://content.example.com/release/files/assets/crate.glb" -> model
            else -> null
        }
        when {
            request.headers[HttpHeaders.Authorization] != "Bearer player-token" -> respond(ByteArray(0), HttpStatusCode.Unauthorized)
            body == null -> respond(ByteArray(0), HttpStatusCode.NotFound)
            body === model && rateLimited++ == 0 -> respond(ByteArray(0), HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "2"))
            else -> respond(body, HttpStatusCode.OK)
        }
    }

    /** Stands in for the game's own server, which signs its player in and hands out read tokens. */
    private class GameBackend {
        class TokenGrant(val token: String, val expiresInSeconds: Long)

        suspend fun requestReadToken(): TokenGrant = TokenGrant("player-token", expiresInSeconds = 3600)
    }

    @Test
    fun aGameReadsProtectedContentThroughItsKtorClient() = runTest {
        val gameBackend = GameBackend()
        val cache = MemoryContentCache()

        // --8<-- [start:ktor]
        // The client your game already has, with the engine it chose: OkHttp, Darwin, CIO or Js.
        val httpClient = HttpClient(engine)
        val transport = KtorContentTransport(httpClient, baseUrl = "https://content.example.com/release/")
        val contentFetch = TokenAuthorizedFetch(transport) {
            val grant = gameBackend.requestReadToken()
            ReadToken(grant.token, expiresIn = grant.expiresInSeconds.seconds)
        }
        val source = IndexedAssetSource.open(fetch = contentFetch, cache = cache)
        // --8<-- [end:ktor]

        val crate = source.read(AssetPath("assets/crate.glb")).getOrThrow()

        assertContentEquals(model, crate)
        assertEquals(2_000, currentTime, "the 429's Retry-After: 2 was waited out, in virtual time")
        assertEquals(3, seen.size, "the index, the 429, and the file sent again")
        assertTrue(seen.all { (_, authorization) -> authorization == "Bearer player-token" })
    }

    @Test
    fun aGameTunesTheBackoff() = runTest {
        val gameBackend = GameBackend()

        // --8<-- [start:backoff]
        val contentFetch = TokenAuthorizedFetch(
            transport = KtorContentTransport(HttpClient(engine), "https://content.example.com/release/"),
            // At most 3 sends of a request answered 429, and 10 seconds of waiting per fetch.
            rateLimitBackoff = RateLimitBackoff(maxAttempts = 3, maxTotalWait = 10.seconds),
        ) {
            val grant = gameBackend.requestReadToken()
            ReadToken(grant.token, expiresIn = grant.expiresInSeconds.seconds)
        }
        // --8<-- [end:backoff]

        assertContentEquals(model, contentFetch.fetch("files/assets/crate.glb"))
        assertEquals(2, seen.size)
    }

    private class MemoryContentCache : ContentCache {
        val stored = mutableMapOf<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }
}
