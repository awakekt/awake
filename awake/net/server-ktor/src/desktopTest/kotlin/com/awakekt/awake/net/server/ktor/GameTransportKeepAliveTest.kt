/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionCloseReason
import com.awakekt.awake.net.TransportServer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.prepareRequest
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GameTransportKeepAliveTest {

    private suspend fun HttpClient.rawWebSocket(
        path: String,
        block: suspend ClientWebSocketSession.() -> Unit,
    ) {
        val statement = prepareRequest {
            url {
                protocol = URLProtocol.WS
                encodedPath = path
            }
        }
        statement.body<ClientWebSocketSession, Unit> { session ->
            try {
                session.block()
            } finally {
                runCatching { session.close() }
                session.outgoing.close()
                session.incoming.cancel()
            }
        }
    }

    @Test
    fun defaultConfigUsesFifteenSecondPingAndTimeout() {
        val config = GameTransportConfig()
        assertEquals(15.seconds, config.pingPeriod)
        assertEquals(15.seconds, config.timeout)
    }

    @Test
    fun idlePeerAnsweringWebSocketPingsRemainsConnectedAcrossPingWindows() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/keepalive"
                pingPeriod = 30.milliseconds
                timeout = 150.milliseconds
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }
        client.webSocket("/keepalive") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                delay(320)
                assertNull(session.closeReason)
                assertFalse(session.incoming().isClosedForReceive)

                send(Frame.Binary(true, "still-here".encodeToByteArray()))
                assertEquals("still-here", session.incoming().receive().decodeToString())

                session.send(DeliveryChannel.Reliable, "ack".encodeToByteArray())
                val reply = incoming.receive() as Frame.Binary
                assertEquals("ack", reply.readBytes().decodeToString())
            }
        }
    }

    @Test
    fun frozenPeerIgnoringWebSocketPingsClosesWithPeerClosedAfterTimeout() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/frozen"
                pingPeriod = 30.milliseconds
                timeout = 60.milliseconds
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }
        client.rawWebSocket("/frozen") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                var sawPing = false
                var closeReason: CloseReason? = null
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Ping -> sawPing = true
                        is Frame.Close -> {
                            closeReason = frame.readReason()
                            break
                        }
                        else -> Unit
                    }
                }
                assertTrue(sawPing, "Expected server to emit a WebSocket Ping frame.")
                assertEquals(CloseReason.Codes.INTERNAL_ERROR.code, closeReason?.code)

                while (!session.incoming().receiveCatching().isClosed) {
                    delay(5)
                }
                assertEquals(SessionCloseReason.PeerClosed, session.closeReason)
            }
        }
    }

    @Test
    fun zeroPingPeriodDisablesServerWebSocketPings() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/zero"
                pingPeriod = Duration.ZERO
                timeout = 40.milliseconds
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }
        client.rawWebSocket("/zero") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                delay(120)
                assertTrue(incoming.tryReceive().isFailure, "Expected no server Ping frames for /zero.")
                assertNull(session.closeReason)

                send(Frame.Binary(true, "awake".encodeToByteArray()))
                assertEquals("awake", session.incoming().receive().decodeToString())
            }
        }
    }

    @Test
    fun infinitePingPeriodAlsoDisablesServerWebSocketPings() = testApplication {
        lateinit var transport: TransportServer
        application {
            install(GameTransport) {
                path = "/infinite"
                pingPeriod = Duration.INFINITE
                timeout = 40.milliseconds
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }
        client.rawWebSocket("/infinite") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                delay(120)
                assertTrue(incoming.tryReceive().isFailure)
                assertNull(session.closeReason)
            }
        }
    }

    @Test
    fun gameTransportCoexistsWithPreinstalledWebSocketsAndAppliesRoutePingConfig() = testApplication {
        lateinit var transport: TransportServer
        application {
            // Host application installs WebSockets first with Ktor's default (pings disabled).
            install(io.ktor.server.websocket.WebSockets)
            install(GameTransport) {
                path = "/shared-ws"
                pingPeriod = 35.milliseconds
                timeout = 2.seconds
            }
            transport = gameTransport
        }

        val client = createClient { install(WebSockets) }
        client.rawWebSocket("/shared-ws") {
            withTimeout(5_000) {
                val session = transport.sessions().receive()
                val firstFrame = incoming.receive()
                assertTrue(firstFrame is Frame.Ping, "Expected route-level Ping even when WebSockets was preinstalled.")
                assertNull(session.closeReason)
            }
        }
    }

    @Test
    fun invalidKeepAliveOrFrameConfigFailsFastOnInstall() {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(GameTransport) { pingPeriod = (-1).seconds }
                }
                startApplication()
            }
        }
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(GameTransport) { timeout = (-1).seconds }
                }
                startApplication()
            }
        }
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(GameTransport) { timeout = Duration.INFINITE }
                }
                startApplication()
            }
        }
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(GameTransport) { maxFrameSize = 0L }
                }
                startApplication()
            }
        }
    }
}
