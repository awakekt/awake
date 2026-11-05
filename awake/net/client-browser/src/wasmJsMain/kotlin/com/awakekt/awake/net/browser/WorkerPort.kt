/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import kotlin.js.ExperimentalWasmJsInterop

/**
 * The page's handle on a socket running in `socket-worker.js`: sends transfer each packet's buffer,
 * and [schedule] waits on the dedicated worker's unthrottled clock before running its action on the page.
 */
internal class WorkerPort(private val worker: JsAny) : SocketPort {
    private val pending = HashMap<Int, () -> Unit>()
    private var nextId = 0
    private var closed = false

    internal val pendingCount: Int
        get() = pending.size

    init {
        onWorkerScheduled(worker, ::runScheduled)
    }

    /** One copy out of linear memory into a new `ArrayBuffer`, which then moves to the worker without another. */
    override fun send(packet: ByteArray) {
        if (!closed) postBuffer(worker, packet.toArrayBuffer())
    }

    override fun close() {
        if (closed) return
        closed = true
        pending.clear()
        postClose(worker)
    }

    override fun schedule(millis: Int, action: () -> Unit) {
        if (closed) return
        val id = ++nextId
        pending[id] = action
        postSchedule(worker, id, millis.coerceAtLeast(0))
    }

    override fun bufferedAmount(): Long = 0L

    fun cancelScheduled() {
        closed = true
        pending.clear()
    }

    private fun runScheduled(id: Int) {
        if (closed) return
        pending.remove(id)?.invoke()
    }
}

@JsFun("(w, scheduled) => w.addEventListener('message', (e) => { if (e.data && e.data.t === 's') scheduled(e.data.id | 0); })")
private external fun onWorkerScheduled(worker: JsAny, scheduled: (Int) -> Unit)

/** [buffer] to the worker, transferred rather than copied. */
@JsFun("(w, b) => w.postMessage(b, [b])")
private external fun postBuffer(worker: JsAny, buffer: JsAny)

@JsFun("(w, id, ms) => w.postMessage({ t: 's', id, ms })")
private external fun postSchedule(worker: JsAny, id: Int, millis: Int)

@JsFun("(w) => w.postMessage({ t: 'close' })")
private external fun postClose(worker: JsAny)
