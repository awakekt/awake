/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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

    @Test
    fun streamingOptionsReportSanitizedChunkProgress() = runTest {
        val reported = mutableListOf<Pair<String, Int>>()
        val transport = BrowserContentTransport(
            options = BrowserTransportOptions(
                maxAttempts = 1,
                onChunk = { url, bytesRead -> reported += url to bytesRead },
            ),
        )

        val response = transport.send(
            ContentRequest("data:application/octet-stream;base64,AAECAwQFBg=="),
        )

        assertEquals(200, response.status)
        assertContentEquals(ByteArray(7) { it.toByte() }, response.body)
        assertTrue(reported.isNotEmpty(), "expected at least one onChunk callback")
        assertEquals(7, reported.last().second)
        assertTrue("?" !in reported.last().first)
    }

    @Test
    fun multiChunkStreamAndArrayBufferFallbackAndTimeoutsWorkInBrowser() = runTest {
        // 1. Multi-chunk ReadableStream assembly + cumulative onChunk progress + token-free URL.
        val progress = mutableListOf<Pair<String, Int>>()
        val streamed = withStubbedFetch(installMultiChunkFetchStub()) {
            BrowserContentTransport(
                baseUrl = "https://content.example.com/release/",
                options = BrowserTransportOptions(
                    maxAttempts = 1,
                    onChunk = { url, bytes -> progress += url to bytes },
                ),
            ).send(ContentRequest("bundles/level.awkb?access_token=s3cret"))
        }
        assertEquals(200, streamed.status)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7), streamed.body)
        assertEquals(listOf("bundles/level.awkb" to 3, "bundles/level.awkb" to 7), progress)

        // 2. Fallback to response.arrayBuffer() when response.body.getReader is absent.
        val fallbackProgress = mutableListOf<Int>()
        val fallback = withStubbedFetch(installArrayBufferOnlyFetchStub()) {
            BrowserContentTransport(
                options = BrowserTransportOptions(
                    maxAttempts = 1,
                    onChunk = { _, bytes -> fallbackProgress += bytes },
                ),
            ).send(ContentRequest("https://content.example.com/fallback.bin"))
        }
        assertEquals(200, fallback.status)
        assertContentEquals(byteArrayOf(9, 8, 7, 6), fallback.body)
        assertEquals(listOf(4), fallbackProgress)

        // 3. Connect timeout aborts a hung fetch() and reports a timeout failure.
        val connectFailure = assertFailsWith<IllegalStateException> {
            withStubbedFetch(installHungConnectFetchStub()) {
                BrowserContentTransport(
                    options = BrowserTransportOptions(
                        connectTimeoutMs = 40L,
                        stallTimeoutMs = 5_000L,
                        totalTimeoutMs = 5_000L,
                        maxAttempts = 1,
                    ),
                ).send(ContentRequest("https://content.example.com/slow-connect.bin"))
            }
        }
        assertTrue("connect timeout" in connectFailure.message.orEmpty(), connectFailure.message)

        // 4. Stall timeout aborts a stream that stops yielding chunks.
        val stallFailure = assertFailsWith<IllegalStateException> {
            withStubbedFetch(installStalledStreamFetchStub()) {
                BrowserContentTransport(
                    options = BrowserTransportOptions(
                        connectTimeoutMs = 5_000L,
                        stallTimeoutMs = 40L,
                        totalTimeoutMs = 5_000L,
                        maxAttempts = 1,
                    ),
                ).send(ContentRequest("https://content.example.com/stalled.bin"))
            }
        }
        assertTrue("stall timeout" in stallFailure.message.orEmpty(), stallFailure.message)
    }

    private suspend fun <T> withStubbedFetch(previousFetch: kotlin.js.JsAny?, block: suspend () -> T): T =
        try {
            block()
        } finally {
            restoreFetch(previousFetch)
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

@JsFun(
    """
() => {
  const prev = globalThis.fetch;
  globalThis.fetch = () => Promise.resolve({
    status: 200,
    headers: new Headers({ 'content-type': 'application/octet-stream' }),
    body: {
      getReader() {
        const parts = [new Uint8Array([1, 2, 3]), new Uint8Array([4, 5, 6, 7])];
        let index = 0;
        return {
          read() {
            if (index < parts.length) return Promise.resolve({ done: false, value: parts[index++] });
            return Promise.resolve({ done: true, value: undefined });
          },
          cancel() { return Promise.resolve(); },
          releaseLock() {},
        };
      },
    },
  });
  return prev;
}
    """,
)
private external fun installMultiChunkFetchStub(): kotlin.js.JsAny?

@JsFun(
    """
() => {
  const prev = globalThis.fetch;
  globalThis.fetch = () => Promise.resolve({
    status: 200,
    headers: new Headers(),
    body: null,
    arrayBuffer() { return Promise.resolve(new Uint8Array([9, 8, 7, 6]).buffer); },
  });
  return prev;
}
    """,
)
private external fun installArrayBufferOnlyFetchStub(): kotlin.js.JsAny?

@JsFun(
    """
() => {
  const prev = globalThis.fetch;
  globalThis.fetch = (_url, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new Error('Aborted')), { once: true });
  });
  return prev;
}
    """,
)
private external fun installHungConnectFetchStub(): kotlin.js.JsAny?

@JsFun(
    """
() => {
  const prev = globalThis.fetch;
  globalThis.fetch = (_url, init) => Promise.resolve({
    status: 200,
    headers: new Headers(),
    body: {
      getReader() {
        let sentFirst = false;
        let rejectRead = null;
        init.signal.addEventListener('abort', () => {
          if (rejectRead) rejectRead(new Error('Aborted'));
        }, { once: true });
        return {
          read() {
            if (!sentFirst) {
              sentFirst = true;
              return Promise.resolve({ done: false, value: new Uint8Array([1, 2]) });
            }
            return new Promise((_resolve, reject) => { rejectRead = reject; });
          },
          cancel() {
            if (rejectRead) rejectRead(new Error('Cancelled'));
            return Promise.resolve();
          },
          releaseLock() {},
        };
      },
    },
  });
  return prev;
}
    """,
)
private external fun installStalledStreamFetchStub(): kotlin.js.JsAny?

@JsFun("(prev) => { globalThis.fetch = prev; }")
private external fun restoreFetch(prev: kotlin.js.JsAny?)
