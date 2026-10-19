/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.TransportSession
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.websocket.CloseReason
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
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for [GameTransport].
 */
class GameTransportConfig {
    /** WebSocket route path (default: `"/net"`). */
    var path: String = "/net"

    /** Ping period interval (default: 15s). */
    var pingPeriod: Duration = 15.seconds

    /** Connection timeout duration (default: 15s). */
    var timeout: Duration = 15.seconds

    /** Maximum inbound frame size in bytes (default: 64 KB). */
    var maxFrameSize: Long = 64 * 1024

    /** Inbound packet queue capacity per session (default: 128). */
    var inboxCapacity: Int = 128

    /** Outbound packet queue capacity per session before a slow client is dropped (default: 128). */
    var outboundCapacity: Int = 128

    /** Port assigned or queried (used when reporting [TransportServer.port]). */
    var port: Int = 0
}

/**
 * Ktor [TransportServer] implementation over Ktor WebSockets.
 */
class KtorGameTransport internal constructor(
    private val config: GameTransportConfig,
) : TransportServer {

    private val accepted = Channel<TransportSession>(Channel.BUFFERED)
    private val nextSessionId = AtomicLong(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val port: Int get() = config.port

    override fun sessions(): ReceiveChannel<TransportSession> = accepted

    internal suspend fun onConnect(socket: WebSocketSession) {
        val id = SessionId(nextSessionId.getAndIncrement())
        val session = KtorTransportSession(
            id = id,
            socket = socket,
            inboxCapacity = config.inboxCapacity,
            outboundCapacity = config.outboundCapacity,
            maxFrameSize = config.maxFrameSize,
        )
        accepted.send(session)
        session.pump()
    }

    override suspend fun stop() {
        accepted.close()
        scope.cancel()
    }
}

/** Attribute key for retrieving [KtorGameTransport] from [Application.attributes]. */
val GameTransportKey: AttributeKey<KtorGameTransport> = AttributeKey("GameTransport")

/**
 * Ktor plugin installing [KtorGameTransport] into the [Application].
 */
val GameTransport: ApplicationPlugin<GameTransportConfig> =
    createApplicationPlugin("GameTransport", ::GameTransportConfig) {
        val cfg = pluginConfig
        val transport = KtorGameTransport(cfg)
        application.attributes.put(GameTransportKey, transport)

        val pingMs = cfg.pingPeriod.inWholeMilliseconds
        val timeoutMs = cfg.timeout.inWholeMilliseconds
        val maxFrame = cfg.maxFrameSize

        application.install(WebSockets) {
            pingPeriodMillis = pingMs
            timeoutMillis = timeoutMs
            maxFrameSize = maxFrame
        }

        application.routing {
            webSocket(cfg.path) {
                transport.onConnect(this)
            }
        }

        on(MonitoringEvent(ApplicationStopping)) {
            runBlocking { transport.stop() }
        }
    }

/**
 * Accessor for the installed [KtorGameTransport] on [Application].
 */
val Application.gameTransport: TransportServer
    get() = attributes.getOrNull(GameTransportKey)
        ?: error("GameTransport plugin is not installed on this Application.")

internal class KtorTransportSession(
    override val id: SessionId,
    private val socket: WebSocketSession,
    inboxCapacity: Int,
    outboundCapacity: Int,
    private val maxFrameSize: Long,
) : TransportSession {

    private val inbox = Channel<ByteArray>(inboxCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val outbox = Channel<ByteArray>(outboundCapacity, onBufferOverflow = BufferOverflow.SUSPEND)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            try {
                for (packet in outbox) {
                    socket.send(Frame.Binary(fin = true, data = packet))
                }
            } finally {
                socket.close(CloseReason(CloseReason.Codes.NORMAL, "Outbox closed"))
            }
        }
    }

    suspend fun pump() {
        try {
            socket.incoming.consumeAsFlow()
                .mapNotNull { (it as? Frame.Binary)?.readBytes() }
                .takeWhile { it.size <= maxFrameSize }
                .collect { packet ->
                    inbox.send(packet)
                }
        } finally {
            close()
        }
    }

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        val sent = outbox.trySend(packet)
        if (sent.isFailure) {
            // Outbound buffer full: slow client is terminated rather than unbounded buffering
            socket.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Client send buffer overflow"))
            close()
        }
    }

    override fun incoming(): ReceiveChannel<ByteArray> = inbox

    override suspend fun close() {
        inbox.close()
        outbox.close()
        scope.cancel()
        socket.close()
    }
}
