/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Opens the socket at [url] on the page's own thread, and returns it once it has opened. Fails with
 * [BrowserSocketFailed] when the socket closes before it opens.
 */
internal suspend fun connectOnPage(url: String): BrowserSocketTransport = suspendCancellableCoroutine { waiting ->
    val socket = newSocket(url)
    if (socket == null) {
        waiting.resumeWithException(BrowserSocketFailed(url, ABNORMAL_CLOSE))
        return@suspendCancellableCoroutine
    }
    var connection: SocketConnection? = null
    onSocketEvents(
        socket,
        opened = {
            val opened = SocketConnection(PagePort(socket), runsInWorker = false, nowMillis = ::epochMillis)
            connection = opened
            if (waiting.isActive) waiting.resume(opened)
        },
        message = { data -> connection?.received(data.toByteArray()) },
        closed = { code, reason ->
            val opened = connection
            if (opened != null) {
                opened.closed(code, reason)
            } else if (waiting.isActive) {
                waiting.resumeWithException(BrowserSocketFailed(url, code))
            }
        },
    )
    waiting.invokeOnCancellation { closeSocket(socket) }
}

private class PagePort(private val socket: JsAny) : SocketPort {
    override fun send(packet: ByteArray) = sendBuffer(socket, packet.toArrayBuffer())

    override fun close() = closeSocket(socket)

    override fun schedule(millis: Int, action: () -> Unit) = setTimeout(millis, action)

    override fun bufferedAmount(): Long = bufferedAmountOf(socket).toLong()
}

/** A binary socket on the page, or null when the browser won't open [url] (not `ws:` or `wss:`, or malformed). */
@JsFun("(url) => { try { const s = new WebSocket(url); s.binaryType = 'arraybuffer'; return s; } catch (_) { return null; } }")
private external fun newSocket(url: String): JsAny?

/** The page socket's events: [message] with each binary message (a text message is not a packet), [closed] once. */
@JsFun(
    """(s, opened, message, closed) => {
        s.addEventListener('open', () => opened());
        s.addEventListener('message', (e) => { if (e.data instanceof ArrayBuffer) message(e.data); });
        s.addEventListener('close', (e) => closed(e.code, String(e.reason || '')));
    }""",
)
private external fun onSocketEvents(socket: JsAny, opened: () -> Unit, message: (JsAny) -> Unit, closed: (Int, String) -> Unit)

@JsFun("(s) => s.bufferedAmount")
private external fun bufferedAmountOf(socket: JsAny): Double

@JsFun("(s, b) => { if (s.readyState === 1) s.send(b); }")
private external fun sendBuffer(socket: JsAny, buffer: JsAny)

@JsFun("(s) => { try { s.close(1000); } catch (_) {} }")
private external fun closeSocket(socket: JsAny)
