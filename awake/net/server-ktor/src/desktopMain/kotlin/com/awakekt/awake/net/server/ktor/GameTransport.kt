/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.InboundDropMetrics
import com.awakekt.awake.net.SessionCloseReason
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportServer
import com.awakekt.awake.net.TransportSession
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val transportLogger = KtorSimpleLogger("com.awakekt.awake.net.server.ktor.GameTransport")

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

    /**
     * Resolves [TransportSession.principal] from the accepted upgrade request, once per
     * connection, before the session is handed to [TransportServer.sessions] (default: none).
     *
     * Read only what an earlier step already verified, typically a value a route-scoped plugin
     * put in [ApplicationCall.attributes] after checking the request; never trust raw headers or
     * query parameters here. Throwing refuses the connection.
     */
    var principal: (ApplicationCall) -> String? = { null }
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

    internal suspend fun onConnect(socket: WebSocketSession, principal: String? = null) {
        val id = SessionId(nextSessionId.getAndIncrement())
        val session = KtorTransportSession(
            id = id,
            principal = principal,
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
                transport.onConnect(this, cfg.principal(call))
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
    override val principal: String?,
    private val socket: WebSocketSession,
    inboxCapacity: Int,
    private val outboundCapacity: Int,
    private val maxFrameSize: Long,
    writerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val closeTimeout: Duration = 1.seconds,
    private val logger: Logger = transportLogger,
) : TransportSession,
    InboundDropMetrics {

    private val discardedInbound = AtomicLong(0)
    override val droppedInbound: Long get() = discardedInbound.get()

    private val inbox = Channel<ByteArray>(
        inboxCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { discardedInbound.incrementAndGet() },
    )
    private val outbox = Channel<ByteArray>(outboundCapacity, onBufferOverflow = BufferOverflow.SUSPEND)
    private val scope = CoroutineScope(SupervisorJob() + writerDispatcher)
    private val closing = AtomicBoolean(false)
    private val reason = AtomicReference<SessionCloseReason?>(null)
    override val closeReason: SessionCloseReason? get() = reason.get()
    private val closed = CompletableDeferred<Unit>()

    // Observe deferred failures as well as closing the transport in finally.
    private val writer = scope.async {
        try {
            for (packet in outbox) {
                socket.send(Frame.Binary(fin = true, data = packet))
            }
        } finally {
            // A writer that ends on its own (a failed write) means the peer is gone.
            reason.compareAndSet(null, SessionCloseReason.PeerClosed)
            inbox.close()
            outbox.close()
            val flushed = withTimeoutOrNull(closeTimeout) {
                socket.close(CloseReason(CloseReason.Codes.NORMAL, "Outbox closed"))
                true
            } == true
            if (!flushed) socket.cancel()
        }
    }.also { task ->
        task.invokeOnCompletion { failure ->
            if (failure != null && failure !is CancellationException) {
                logger.warn("Transport session $id writer failed", failure)
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
            finish(drain = true, SessionCloseReason.PeerClosed)
        }
    }

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        val sent = outbox.trySend(packet)
        if (sent.isFailure && !sent.isClosed) {
            // Outbound buffer full: slow client is terminated rather than unbounded buffering
            finish(drain = false, SessionCloseReason.OutboundOverflow)
        }
    }

    override fun incoming(): ReceiveChannel<ByteArray> = inbox

    /**
     * Drain accepted packets in order, then let the writer enqueue and flush the close frame.
     * The one-second deadline covers the drain and flush together. It is not peer acknowledgement;
     * peer failure, timeout and outbound overflow may discard packets.
     */
    override suspend fun close() = finish(drain = true, SessionCloseReason.ServerClosed)

    private suspend fun finish(drain: Boolean, cause: SessionCloseReason) = withContext(NonCancellable) {
        if (closing.compareAndSet(false, true)) {
            try {
                // Record the cause before the inbox closes, so the host reads it on seeing the close.
                reason.compareAndSet(null, cause)
                if (cause == SessionCloseReason.OutboundOverflow) {
                    logger.warn("Transport session $id closed: outbound queue overflow (capacity $outboundCapacity)")
                }
                inbox.close()
                outbox.close()
                val flushed = withTimeoutOrNull(closeTimeout) {
                    if (drain) {
                        writer.join()
                    } else {
                        writer.cancel()
                        socket.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Client send buffer overflow"))
                    }
                    true
                } == true
                if (!flushed) socket.cancel()
            } finally {
                scope.cancel()
                closed.complete(Unit)
            }
        } else {
            closed.await()
        }
    }
}
