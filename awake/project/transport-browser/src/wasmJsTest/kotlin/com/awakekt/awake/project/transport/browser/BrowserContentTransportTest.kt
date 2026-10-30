/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.transport.browser

import com.awakekt.awake.project.ContentRequest
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.ReadToken
import com.awakekt.awake.project.TokenAuthorizedFetch
import com.awakekt.awake.project.platformContentCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The real `fetch()` call, in a browser: bytes and status come back, and a failure is reported, not thrown by the browser. */
class BrowserContentTransportTest {
    @Test
    fun aResponseBodyAndStatusComeBackWithTheHeadersSent() = runTest {
        // Bytes 0 to 6: a body longer than one 32-bit word, with a tail.
        val response = BrowserContentTransport().send(
            ContentRequest("data:application/octet-stream;base64,AAECAwQFBg==", mapOf("Authorization" to "Bearer t0ken")),
        )

        assertEquals(200, response.status)
        assertContentEquals(ByteArray(7) { it.toByte() }, response.body)
        assertEquals("application/octet-stream", response.header("Content-Type"))
    }

    @Test
    fun aUrlThatDoesNotResolveIsAFailureNamingIt() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            BrowserContentTransport(baseUrl = "not a base URL").send(ContentRequest("files/a.glb?access_token=s3cret"))
        }

        assertTrue("files/a.glb:" in failure.message.orEmpty(), failure.message)
        assertTrue("s3cret" !in failure.message.orEmpty(), failure.message)
    }

    /** The "Protected content" guide's sample: compiled here, and not run, since it reads from a real content server. */
    @Suppress("unused")
    private suspend fun guideSample(gameBackend: GameBackend): IndexedAssetSource {
        // --8<-- [start:browser]
        val transport = BrowserContentTransport(baseUrl = "https://content.example.com/release/")
        val contentFetch = TokenAuthorizedFetch(transport) {
            val grant = gameBackend.requestReadToken()
            ReadToken(grant.token, expiresIn = grant.expiresInSeconds.seconds)
        }
        val source = IndexedAssetSource.open(fetch = contentFetch, cache = platformContentCache("harbor-town"))
        // --8<-- [end:browser]
        return source
    }

    /** Stands in for the game's own server, which signs its player in and hands out read tokens. */
    private class GameBackend {
        class TokenGrant(val token: String, val expiresInSeconds: Long)

        suspend fun requestReadToken(): TokenGrant = TokenGrant("player-token", expiresInSeconds = 3600)
    }
}
