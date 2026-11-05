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
internal suspend fun connectInWorker(
    url: String,
    script: String,
    holdWakeLock: Boolean = true,
    acquireWakeLock: () -> WakeLock = ::acquireBrowserWakeLock,
): BrowserSocketTransport = suspendCancellableCoroutine { waiting ->
    val worker = if (workerDefined()) newWorker(script) else null
    if (worker == null) {
        waiting.resumeWithException(SocketWorkerFailed(script))
        return@suspendCancellableCoroutine
    }
    var port: WorkerPort? = null
    var connection: SocketConnection? = null
    onWorkerMessages(
        worker,
        opened = {
            if (waiting.isActive) {
                val workerPort = WorkerPort(worker)
                val opened = SocketConnection(
                    port = workerPort,
                    runsInWorker = true,
                    nowMillis = ::epochMillis,
                    holdWakeLock = holdWakeLock,
                    acquireWakeLock = acquireWakeLock,
                )
                port = workerPort
                connection = opened
                waiting.resume(opened)
            } else {
                terminate(worker)
            }
        },
        message = { data, at -> connection?.received(data.toByteArray(), at) },
        buffered = { amount, taken -> connection?.buffered(amount.toLong(), taken.toLong()) },
        closed = { code, reason ->
            port?.cancelScheduled()
            terminate(worker)
            val opened = connection
            if (opened != null) {
                opened.closed(code, reason)
            } else if (waiting.isActive) {
                waiting.resumeWithException(BrowserSocketFailed(url, code))
            }
        },
    )
    onWorkerError(worker) {
        port?.cancelScheduled()
        terminate(worker)
        val opened = connection
        if (opened != null) {
            opened.closed(ABNORMAL_CLOSE, "the socket worker failed")
        } else if (waiting.isActive) {
            waiting.resumeWithException(SocketWorkerFailed(script))
        }
    }
    postOpen(worker, url)
    waiting.invokeOnCancellation {
        port?.cancelScheduled()
        terminate(worker)
    }
}

/** A dedicated worker running [script], or null when the browser refuses to start it (a cross-origin URL). */
@JsFun("(script) => { try { return new Worker(script); } catch (_) { return null; } }")
internal external fun newWorker(script: String): JsAny?

/** The worker's messages to the page, by kind (`socket-worker.js`). */
@JsFun(
    """(w, opened, message, buffered, closed) => {
        w.addEventListener('message', (e) => {
            const m = e.data;
            if (!m) return;
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

@JsFun("(w) => w.terminate()")
internal external fun terminate(worker: JsAny)
