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
 * Opens the socket at [url] in a dedicated worker running [script] (`socket-worker.js`), and returns
 * the page's side once it has opened. Fails with [SocketWorkerFailed] when the worker can't start or
 * its script doesn't run, and with [BrowserSocketFailed] when the socket closes before it opens.
 */
internal suspend fun connectInWorker(url: String, script: String): BrowserSocketTransport = suspendCancellableCoroutine { waiting ->
    val worker = if (workerDefined()) newWorker(script) else null
    if (worker == null) {
        waiting.resumeWithException(SocketWorkerFailed(script))
        return@suspendCancellableCoroutine
    }
    val connection = SocketConnection(WorkerPort(worker), runsInWorker = true, nowMillis = ::epochMillis)
    onWorkerMessages(
        worker,
        opened = { if (waiting.isActive) waiting.resume(connection) },
        message = { data, at -> connection.received(data.toByteArray(), at) },
        buffered = { amount, taken -> connection.buffered(amount.toLong(), taken.toLong()) },
        closed = { code, reason ->
            terminate(worker)
            if (waiting.isActive) waiting.resumeWithException(BrowserSocketFailed(url, code)) else connection.closed(code, reason)
        },
    )
    onWorkerError(worker) {
        terminate(worker)
        if (waiting.isActive) {
            waiting.resumeWithException(SocketWorkerFailed(script))
        } else {
            connection.closed(ABNORMAL_CLOSE, "the socket worker failed")
        }
    }
    postOpen(worker, url)
    waiting.invokeOnCancellation { terminate(worker) }
}

private class WorkerPort(private val worker: JsAny) : SocketPort {
    /** One copy out of linear memory into a new `ArrayBuffer`, which then moves to the worker without another. */
    override fun send(packet: ByteArray) = postBuffer(worker, packet.toArrayBuffer())

    override fun close() = postClose(worker)

    override fun schedule(millis: Int, action: () -> Unit) = setTimeout(millis, action)

    override fun bufferedAmount(): Long = 0L
}

/** A dedicated worker running [script], or null when the browser refuses to start it (a cross-origin URL). */
@JsFun("(script) => { try { return new Worker(script); } catch (_) { return null; } }")
private external fun newWorker(script: String): JsAny?

/** The worker's messages to the page, by kind (`socket-worker.js`). */
@JsFun(
    """(w, opened, message, buffered, closed) => {
        w.addEventListener('message', (e) => {
            const m = e.data;
            switch (m.t) {
                case 'm': message(m.data, m.at); break;
                case 'b': buffered(m.buffered, m.taken); break;
                case 'open': opened(); break;
                case 'x': closed(m.code, String(m.reason || '')); break;
            }
        });
    }""",
)
private external fun onWorkerMessages(
    worker: JsAny,
    opened: () -> Unit,
    message: (JsAny, Double) -> Unit,
    buffered: (Double, Double) -> Unit,
    closed: (Int, String) -> Unit,
)

/** The worker's own `error` event: its script didn't load, or threw. */
@JsFun("(w, failed) => w.addEventListener('error', (e) => { e.preventDefault(); failed(); })")
private external fun onWorkerError(worker: JsAny, failed: () -> Unit)

@JsFun("(w, url) => w.postMessage({ t: 'open', url })")
private external fun postOpen(worker: JsAny, url: String)

/** [buffer] to the worker, transferred rather than copied. */
@JsFun("(w, b) => w.postMessage(b, [b])")
private external fun postBuffer(worker: JsAny, buffer: JsAny)

@JsFun("(w) => w.postMessage({ t: 'close' })")
private external fun postClose(worker: JsAny)

@JsFun("(w) => w.terminate()")
private external fun terminate(worker: JsAny)
