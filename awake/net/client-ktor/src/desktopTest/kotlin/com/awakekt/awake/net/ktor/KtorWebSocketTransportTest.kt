/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.ktor

import com.awakekt.awake.net.ConnectionState
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.server.ktor.GameTransport
import com.awakekt.awake.net.server.ktor.gameTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

class KtorWebSocketTransportTest {
    private fun startLoopbackServer(): TransportServer {
        val engine = embeddedServer(ServerCIO, port = 0, host = LOOPBACK) {
            install(GameTransport) {
                path = "/play"
                pingPeriod = 15.seconds
                timeout = 30.seconds
                outboundCapacity = 1_024
                inboxCapacity = 1_024
            }
        }.start(wait = false)
        val boundPort = runBlocking { engine.engine.resolvedConnectors().first().port }
        val ktor = engine.application.gameTransport
        return object : TransportServer by ktor {
            override val port: Int = boundPort
            override suspend fun stop() {
                ktor.stop()
                engine.stop(gracePeriodMillis = 100L, timeoutMillis = 1_000L)
            }
        }
    }

    @Test
    fun packetSentImmediatelyBeforeServerCloseArrivesBeforeChannelCloses(): Unit = runBlocking {
        val server = startLoopbackServer()
        val serving = launch {
            for (session in server.sessions()) {
                launch {
                    session.send(DeliveryChannel.Reliable, KICK_PACKET)
                    session.close()
                }
            }
        }
        try {
            repeat(RAPID_CONNECTIONS) { attempt ->
                val client = connectWebSocket("ws://$LOOPBACK:${server.port}/play")
                withTimeout(5_000L) {
                    assertContentEquals(
                        KICK_PACKET,
                        client.incoming().receiveCatching().getOrNull(),
                        "connection $attempt",
                    )
                    assertTrue(client.incoming().receiveCatching().isClosed)
                }
                client.close()
            }
        } finally {
            serving.cancel()
            server.stop()
        }
    }

    @Test
    fun burstLargerThanInboundCapacitySuspendsWithoutDroppingPackets(): Unit = runBlocking {
        val server = startLoopbackServer()
        val serving = launch {
            for (session in server.sessions()) {
                launch {
                    repeat(BURST_COUNT) { i ->
                        session.send(DeliveryChannel.Reliable, byteArrayOf((i shr 8).toByte(), i.toByte()))
                    }
                }
            }
        }
        try {
            val client = connectWebSocket(
                url = "ws://$LOOPBACK:${server.port}/play",
                inboundCapacity = SMALL_INBOUND,
            )
            withTimeout(5_000L) {
                repeat(BURST_COUNT) { i ->
                    assertContentEquals(
                        byteArrayOf((i shr 8).toByte(), i.toByte()),
                        client.incoming().receiveCatching().getOrNull(),
                        "packet $i",
                    )
                }
            }
            client.close()
        } finally {
            serving.cancel()
            server.stop()
        }
    }

    @Test
    fun sendsAfterServerClosesAreDroppedCleanlyAndAbortDisconnectsImmediately(): Unit = runBlocking {
        val server = startLoopbackServer()
        val serving = launch {
            for (session in server.sessions()) {
                launch { session.close() }
            }
        }
        try {
            val client = connectWebSocket("ws://$LOOPBACK:${server.port}/play")
            withTimeout(5_000L) {
                repeat(POST_CLOSE_SENDS) { client.send(DeliveryChannel.Unreliable, ByteArray(PACKET_SIZE)) }
                assertTrue(client.incoming().receiveCatching().isClosed)
                repeat(POST_CLOSE_SENDS) { client.send(DeliveryChannel.Unreliable, ByteArray(PACKET_SIZE)) }
                assertEquals(ConnectionState.Disconnected, client.state.value)
            }
            client.close()

            val customHttp = HttpClient(ClientCIO) {
                install(WebSockets) { boundOutgoingFrames(frames = 2) }
            }
            val customTransport = connectWebSocket(
                url = "ws://$LOOPBACK:${server.port}/play",
                client = customHttp,
                closeClient = true,
            )
            customTransport.abort()
            assertEquals(ConnectionState.Disconnected, customTransport.state.value)
        } finally {
            serving.cancel()
            server.stop()
        }

        assertFailsWith<IllegalArgumentException> {
            HttpClient(ClientCIO) { install(WebSockets) { boundOutgoingFrames(frames = 0) } }
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val RAPID_CONNECTIONS = 40
        const val SMALL_INBOUND = 32
        const val BURST_COUNT = 128
        const val POST_CLOSE_SENDS = 500
        const val PACKET_SIZE = 64
        val KICK_PACKET = byteArrayOf(7, 1, 2)
    }
}
