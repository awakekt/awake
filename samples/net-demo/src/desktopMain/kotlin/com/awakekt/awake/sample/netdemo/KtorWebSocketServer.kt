/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.TransportSession
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicLong

/**
 * Ktor WebSocket [TransportServer]. Desktop-only: a browser can host nothing, so the server
 * half has no reason to be common. Every WebSocket message is one packet; the server never
 * concatenates or splits them, which is what keeps this interchangeable with a real datagram
 * transport later.
 *
 * Netty rather than CIO, for both modes: the CIO server engine throws
 * "CIO Engine does not currently support HTTPS" on any `sslConnector`. Running one engine for
 * plaintext and another for TLS would mean the mode you develop against is not the mode you
 * ship.
 */
class KtorWebSocketServer(
    requestedPort: Int = 0,
    private val path: String = "/net",
    private val secure: Boolean = false,
) : TransportServer {
    override val port: Int = if (requestedPort != 0) requestedPort else freePort()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val accepted = Channel<TransportSession>(Channel.BUFFERED)
    private val nextSessionId = AtomicLong(1)
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        server = if (secure) startSecure() else embeddedServer(Netty, port = port) { module() }
        server?.start(wait = false)
    }

    /**
     * TLS listener only -- there is deliberately no plaintext connector alongside it. Offering
     * both would let a client pick the unencrypted one, and which transport security applies
     * is the server's decision, never the client's.
     */
    private fun startSecure(): EmbeddedServer<*, *> {
        val keyStore = DevTls.keyStore()
        return embeddedServer(
            factory = Netty,
            environment = applicationEnvironment(),
            configure = {
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = DevTls.ALIAS,
                    keyStorePassword = { DevTls.passwordChars() },
                    privateKeyPassword = { DevTls.passwordChars() },
                ) {
                    port = this@KtorWebSocketServer.port
                    keyStorePath = DevTls.keyStoreFile
                }
            },
            module = { module() },
        )
    }

    private fun Application.module() {
        install(WebSockets)
        routing {
            webSocket(path) {
                val session = WebSocketTransportSession(SessionId(nextSessionId.getAndIncrement()), this)
                accepted.send(session)
                session.pump()
            }
        }
    }

    override fun sessions(): ReceiveChannel<TransportSession> = accepted

    override suspend fun stop() {
        accepted.close()
        server?.stop(gracePeriodMillis = GRACE_MILLIS, timeoutMillis = TIMEOUT_MILLIS)
        scope.cancel()
    }

    private companion object {
        const val GRACE_MILLIS = 200L
        const val TIMEOUT_MILLIS = 1_000L

        /** Ephemeral port, so parallel tests never collide on a fixed one. */
        fun freePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

/**
 * One connected peer. [pump] runs on the Ktor handler coroutine and must not touch world
 * state -- it only moves bytes into the inbox, which the fixed-tick loop drains. An
 * over-sized packet ends the stream rather than being skipped: it is either an attack or a
 * protocol mismatch, and neither gets to keep sending.
 */
private class WebSocketTransportSession(
    override val id: SessionId,
    private val socket: WebSocketSession,
) : TransportSession {
    // Drop oldest, never suspend: a slow tick loop must not backpressure the socket into
    // holding stale inputs. Losing the oldest input is the correct failure for a tick game.
    private val inbox = Channel<ByteArray>(INBOX_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun pump() {
        try {
            socket.incoming.consumeAsFlow()
                .mapNotNull { (it as? Frame.Binary)?.readBytes() }
                .takeWhile { it.size <= MAX_PACKET_BYTES }
                .collect { inbox.send(it) }
        } finally {
            inbox.close()
        }
    }

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        socket.send(Frame.Binary(fin = true, data = packet))
    }

    override fun incoming(): ReceiveChannel<ByteArray> = inbox

    override suspend fun close() {
        socket.close()
        inbox.close()
    }
}
