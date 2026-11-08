/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.ktor

import com.awakekt.awake.net.AbortableTransport
import com.awakekt.awake.net.ConnectionState
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Default number of outgoing WebSocket frames buffered inside Ktor before `send` suspends (`1`). */
const val OUTGOING_FRAMES: Int = 1

/** Default bounded inbound packet queue capacity for [connectWebSocket] (`256` packets). */
const val DEFAULT_INBOUND_PACKETS: Int = 256

/** Default WebSocket ping interval (`15_000 ms`) configured on the shared default client. */
const val DEFAULT_PING_INTERVAL_MILLIS: Long = 15_000L

private val defaultWebSocketClient: HttpClient by lazy {
    HttpClient {
        install(WebSockets) {
            pingIntervalMillis = DEFAULT_PING_INTERVAL_MILLIS
            boundOutgoingFrames()
        }
    }
}

/**
 * Configures Ktor's WebSocket outgoing frame channel to hold at most [frames] queued frames before
 * suspending callers of `send`.
 *
 * Ktor defaults to an unbounded outgoing frame queue, which hides network congestion from callers
 * and lets seconds of stale movement inputs pile up ahead of latency probes or discrete actions.
 * Bounding the outgoing channel surfaces backpressure directly to the caller's coroutine.
 *
 * @param frames Maximum queued outgoing frames before `send` suspends (defaults to [OUTGOING_FRAMES]).
 */
fun WebSockets.Config.boundOutgoingFrames(frames: Int = OUTGOING_FRAMES) {
    require(frames > 0) { "frames must be positive, was $frames" }
    channels {
        outgoing = bounded(frames)
    }
}

/**
 * Opens a binary WebSocket [AbortableTransport] to [url] using a shared default [HttpClient]
 * configured with [boundOutgoingFrames] and a [DEFAULT_PING_INTERVAL_MILLIS] keepalive probe.
 *
 * The platform's Ktor client engine is discovered from the runtime classpath (for example
 * `ktor-client-cio` on JVM/Android or `ktor-client-js` on wasmJs).
 *
 * @param url Target WebSocket URL (such as `ws://127.0.0.1:8080/play`).
 * @param inboundCapacity Bounded capacity of the suspending [AbortableTransport.incoming] channel.
 * @return Connected [AbortableTransport] ready to exchange binary datagram frames.
 */
suspend fun connectWebSocket(
    url: String,
    inboundCapacity: Int = DEFAULT_INBOUND_PACKETS,
): AbortableTransport = connectWebSocket(
    url = url,
    client = defaultWebSocketClient,
    closeClient = false,
    inboundCapacity = inboundCapacity,
)

/**
 * Opens a binary WebSocket [AbortableTransport] to [url] over a caller-supplied [client].
 *
 * Callers constructing their own [HttpClient] should install `WebSockets { boundOutgoingFrames() }`
 * so outbound sends suspend under socket backpressure rather than buffering unboundedly.
 *
 * @param url Target WebSocket URL (such as `ws://127.0.0.1:8080/play`).
 * @param client Pre-configured Ktor [HttpClient] with the [WebSockets] plugin installed.
 * @param closeClient Whether [AbortableTransport.close] and [AbortableTransport.abort] also close [client].
 * @param inboundCapacity Bounded capacity of the suspending [AbortableTransport.incoming] channel.
 * @return Connected [AbortableTransport] ready to exchange binary datagram frames.
 */
suspend fun connectWebSocket(
    url: String,
    client: HttpClient,
    closeClient: Boolean = false,
    inboundCapacity: Int = DEFAULT_INBOUND_PACKETS,
): AbortableTransport {
    require(inboundCapacity > 0) { "inboundCapacity must be positive, was $inboundCapacity" }
    val socket = client.webSocketSession(url)
    return KtorSocketTransport(
        socket = socket,
        client = client,
        closeClient = closeClient,
        inboundCapacity = inboundCapacity,
    )
}

private class KtorSocketTransport(
    private val socket: DefaultWebSocketSession,
    private val client: HttpClient,
    private val closeClient: Boolean,
    inboundCapacity: Int,
) : AbortableTransport {
    private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected(SessionId(0L)))
    private val inbound = Channel<ByteArray>(inboundCapacity)

    init {
        // Launch on the client's scope rather than the WebSocket session's scope: Ktor cancels
        // session-scoped coroutines as soon as the close handshake finishes, which otherwise drops
        // final binary frames (such as a server kick reason) sent immediately before close.
        client.launch {
            try {
                for (frame in socket.incoming) {
                    if (frame is Frame.Binary) {
                        inbound.send(frame.readBytes())
                    }
                }
            } finally {
                inbound.close()
                connection.value = ConnectionState.Disconnected
            }
        }
    }

    override val state: StateFlow<ConnectionState> = connection

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        try {
            socket.send(Frame.Binary(fin = true, data = packet))
        } catch (_: ClosedSendChannelException) {
            connection.value = ConnectionState.Disconnected
        } catch (_: CancellationException) {
            // Re-throw if the caller's own coroutine was cancelled; otherwise the socket closed while waiting.
            currentCoroutineContext().ensureActive()
            connection.value = ConnectionState.Disconnected
        }
    }

    override fun incoming(): ReceiveChannel<ByteArray> = inbound

    override fun abort() {
        socket.cancel()
        if (closeClient) {
            client.close()
        }
        connection.value = ConnectionState.Disconnected
    }

    override suspend fun close() {
        runCatching { socket.close() }
        if (closeClient) {
            client.close()
        }
        connection.value = ConnectionState.Disconnected
    }
}
