/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.Sha256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The "Protected content" guide includes these samples, so they keep compiling and doing what it says. */
class ProtectedContentDocsSampleTest {
    private val model = ByteArray(64) { it.toByte() }
    private val index = ProjectIndex(
        files = listOf(
            ProjectIndexEntry("assets/crate.glb", model.size.toLong(), Sha256.digestHex(model), "files/assets/crate.glb"),
        ),
    )

    /** Stands in for the game's own HTTP client, which Core doesn't carry. */
    private inner class GameHttpClient {
        val sent = mutableListOf<Pair<String, Map<String, String>>>()

        /** Answers the status, the body and the response headers. */
        suspend fun get(url: String, headers: Map<String, String>): Triple<Int, ByteArray, Map<String, String>> {
            sent += url to headers
            val authorized = headers["Authorization"] == "Bearer player-token" || url.endsWith("access_token=player-token")
            val body = when (url.substringBefore('?')) {
                "https://content.example.com/release/project.index.json" ->
                    Json.encodeToString(ProjectIndex.serializer(), index).encodeToByteArray()
                "https://content.example.com/release/files/assets/crate.glb" -> model
                else -> return Triple(404, ByteArray(0), emptyMap())
            }
            return if (authorized) Triple(200, body, emptyMap()) else Triple(401, ByteArray(0), emptyMap())
        }
    }

    /** Stands in for the game's own server, which signs its player in and hands out read tokens. */
    private class GameBackend {
        class TokenGrant(val token: String, val expiresInSeconds: Long)

        var grants = 0

        suspend fun requestReadToken(): TokenGrant {
            grants++
            return TokenGrant("player-token", expiresInSeconds = 3600)
        }
    }

    @Test
    fun aGameWiresItsTokenProviderAndReadsProtectedContent() = runTest {
        val httpClient = GameHttpClient()
        val gameBackend = GameBackend()
        val cache = MemoryContentCache()

        // --8<-- [start:transport]
        val baseUrl = "https://content.example.com/release/"
        val transport = ContentTransport { request ->
            // Resolve the index's relative URLs, send the headers, and answer every status, 401, 429
            // and 503 included, with the response headers, so their Retry-After is honoured.
            val (status, body, headers) = httpClient.get(baseUrl + request.url, request.headers)
            ContentResponse(status, body, headers)
        }
        // --8<-- [end:transport]

        // --8<-- [start:provider]
        val contentFetch = TokenAuthorizedFetch(transport) {
            // The game's own server signs its player in and answers a short-lived read token.
            val grant = gameBackend.requestReadToken()
            ReadToken(grant.token, expiresIn = grant.expiresInSeconds.seconds)
        }
        // --8<-- [end:provider]

        // --8<-- [start:open]
        val source = IndexedAssetSource.open(fetch = contentFetch, cache = cache)
        source.prefetch(listOf("assets/crate.glb")) { done, total -> println("Downloaded $done of $total bytes") }
        val crate = source.read(AssetPath("assets/crate.glb")).getOrThrow()
        // --8<-- [end:open]

        assertContentEquals(model, crate)
        assertEquals(1, gameBackend.grants, "one token served the index and the file")
        assertEquals(2, httpClient.sent.size, "the index and the file; the read after prefetch came from the cache")
        assertTrue(httpClient.sent.all { (_, headers) -> headers["Authorization"] == "Bearer player-token" })
        assertEquals(setOf(Sha256.digestHex(model)), cache.stored.keys)
    }

    @Test
    fun aHostWithoutHeadersSendsTheTokenInTheQuery() = runTest {
        val httpClient = GameHttpClient()
        val gameBackend = GameBackend()

        // --8<-- [start:query]
        val contentFetch = TokenAuthorizedFetch(
            transport = { request ->
                // This host loads through a URL alone, so the token comes in request.url.
                val (status, body) = httpClient.get("https://content.example.com/release/" + request.url, emptyMap())
                ContentResponse(status, body)
            },
            placement = ReadTokenPlacement.QueryParameter(), // ?access_token=...
        ) {
            val grant = gameBackend.requestReadToken()
            ReadToken(grant.token, expiresIn = grant.expiresInSeconds.seconds)
        }
        // --8<-- [end:query]

        IndexedAssetSource.open(fetch = contentFetch).read(AssetPath("assets/crate.glb")).getOrThrow()

        assertEquals(
            "https://content.example.com/release/files/assets/crate.glb?access_token=player-token",
            httpClient.sent.last().first,
        )
    }

    /**
     * The guide's "Keep files between runs" sample: compiled here, and not run, since it would keep
     * files in this machine's real cache folder. PlatformContentCacheDesktopTest runs the same calls
     * against a temporary folder.
     */
    @Suppress("unused")
    private suspend fun persistentCacheSample(contentFetch: TokenAuthorizedFetch): IndexedAssetSource {
        // --8<-- [start:platform-cache]
        // IndexedDB in a browser, the user's cache folder on desktop, Library/Caches on iOS.
        val cache = platformContentCache(namespace = "harbor-town")
        val source = IndexedAssetSource.open(fetch = contentFetch, cache = cache)
        // --8<-- [end:platform-cache]
        return source
    }

    private class MemoryContentCache : ContentCache {
        val stored = mutableMapOf<String, ByteArray>()
        override suspend fun get(sha256: String): ByteArray? = stored[sha256]
        override suspend fun put(sha256: String, bytes: ByteArray) {
            stored[sha256] = bytes
        }
    }
}
