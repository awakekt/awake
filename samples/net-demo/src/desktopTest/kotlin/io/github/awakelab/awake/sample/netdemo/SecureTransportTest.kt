/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_TIMEOUT_MILLIS = 20_000L

class SecureTransportTest {
    private val server = NetDemoServer(KtorWebSocketServer(secure = true))

    @AfterTest
    fun tearDown() = runBlocking { server.stop() }

    /** Phase 1's remaining exit criterion: the client joins over wss, not ws. */
    @Test
    fun `client joins and receives snapshots over wss`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val client = NetDemoClient(KtorWebSocketClient(port = server.port, secure = true))
            client.connect("secure")
            client.receiveUntil { it.netId != null }
            assertNotNull(client.netId, "no Welcome over TLS")

            client.sendInput(tick = 0, moveX = 1f, moveY = 0f)
            client.receiveUntil { it.snapshot.count == 1 && it.snapshot.x[0] > 0f }
            assertTrue(client.snapshot.x[0] > 0f, "no movement replicated over TLS")
            client.close()
        }
    }

    /**
     * The TLS server must not also answer plaintext. A server that accepts both lets a client
     * choose the unencrypted path, which makes the encryption optional in practice -- the
     * server decides transport security, never the client.
     */
    @Test
    fun `plaintext client cannot reach the tls server`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val plaintext = NetDemoClient(KtorWebSocketClient(port = server.port, secure = false))
            assertFailsWith<Throwable> { plaintext.connect("downgrade") }
            plaintext.close()
        }
    }

    /**
     * A client using the JDK's default trust store has never heard of this self-signed
     * certificate, so its handshake must fail. If this ever stops failing, the trust anchor has
     * quietly become trust-all -- which is the failure mode worth a test of its own, because it
     * looks exactly like everything working.
     */
    @Test
    fun `client using default trust is rejected`() = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            server.start()
            val defaultTrust = HttpClient(CIO) { install(WebSockets) }
            assertFailsWith<Throwable> {
                defaultTrust.webSocketSession {
                    url {
                        protocol = URLProtocol.WSS
                        host = "127.0.0.1"
                        port = server.port
                        path("/net")
                    }
                }
            }
            defaultTrust.close()
        }
    }
}
