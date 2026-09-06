/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.ConnectionState
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.Transport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hard bound applied before any decode, so a hostile packet cannot make the decoder allocate. */
const val MAX_PACKET_BYTES: Int = 1400

internal const val INBOX_CAPACITY = 256

/**
 * Ktor WebSocket [Transport] for one connection. Common to desktop and browser: the engine is
 * whichever one is on the target's classpath -- CIO on desktop, the browser's own WebSocket
 * API via the Js engine on wasmJs. No expect/actual needed, because a socket is the one thing
 * both platforms genuinely agree on.
 *
 * A browser cannot open a UDP socket at all, which is why WebSocket is the transport that
 * ships first (docs/plans/network.md).
 */
class KtorWebSocketClient(
    private val host: String = "127.0.0.1",
    private val port: Int,
    private val path: String = "/net",
    private val secure: Boolean = false,
) : Transport {
    private val client = netHttpClient(secure)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inbox = Channel<ByteArray>(INBOX_CAPACITY)
    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var socket: WebSocketSession? = null

    override val state: StateFlow<ConnectionState> = connectionState.asStateFlow()

    suspend fun connect() {
        connectionState.value = ConnectionState.Connecting
        val session = runCatching {
            client.webSocketSession {
                url {
                    protocol = if (secure) URLProtocol.WSS else URLProtocol.WS
                    host = this@KtorWebSocketClient.host
                    port = this@KtorWebSocketClient.port
                    path(this@KtorWebSocketClient.path)
                }
            }
        }
            .getOrElse {
                connectionState.value = ConnectionState.Failed(it.message ?: "connect failed")
                throw it
            }
        socket = session
        connectionState.value = ConnectionState.Connected(SessionId(0))
        scope.launch {
            try {
                for (frame in session.incoming) {
                    val bytes = (frame as? Frame.Binary)?.readBytes() ?: continue
                    inbox.send(bytes)
                }
            } finally {
                inbox.close()
                connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        socket?.send(Frame.Binary(fin = true, data = packet))
    }

    override fun incoming(): ReceiveChannel<ByteArray> = inbox

    override suspend fun close() {
        socket?.close()
        client.close()
        scope.cancel()
        connectionState.value = ConnectionState.Disconnected
    }
}

/**
 * Per-platform HTTP client. This is a genuine expect/actual rather than a shortcut: on desktop
 * the demo has to be told to trust a self-signed certificate, while a browser has no API for
 * that at all -- trust there belongs to the OS and the user, and a page cannot override it.
 */
internal expect fun netHttpClient(secure: Boolean): HttpClient
