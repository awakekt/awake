/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.browser

import com.awakekt.awake.net.DirectTransport

/**
 * A client connection over a browser's own `WebSocket`, as `connectBrowserSocket` opens it on
 * wasmJs: binary messages only, one message per packet, so it speaks to the Ktor server transport
 * unchanged.
 *
 * Each member is called on the page's thread. Where the socket itself runs is [runsInWorker]:
 * - In a dedicated Web Worker, the socket's events run on the worker's own thread, so a frame that
 *   holds the page's thread can't delay them. The worker stamps each message as it arrives, so
 *   [arrivalLag] is how long the packet then waited for the page; it reads `bufferedAmount` on the
 *   socket's own thread and reports it, so [bufferedBytes] is that report plus the bytes posted to
 *   the worker that it hasn't taken yet.
 * - On the page, the socket's events wait behind the frame like any other task; [arrivalLag] is 0,
 *   since the packet is handed on from the event that brought it, and [bufferedBytes] is the
 *   socket's `bufferedAmount` read at once.
 */
interface BrowserSocketTransport : DirectTransport {
    /** Whether the socket runs in a dedicated Web Worker; false when it runs on the page. */
    val runsInWorker: Boolean

    /** The close event's code once the socket has closed, or null while it is open. For diagnostics. */
    val closeCode: Int?

    /** The close event's reason once the socket has closed; empty while it is open or when none was sent. */
    val closeReason: String
}

/** Where `connectBrowserSocket` runs the browser's `WebSocket`. */
enum class BrowserSocketMode {
    /**
     * In a dedicated Web Worker when the browser has `Worker` and the worker's script runs; on the
     * page otherwise, after saying once why.
     */
    Auto,

    /** In a dedicated Web Worker, failing with [SocketWorkerFailed] when it can't run. */
    Worker,

    /** On the page's own thread, as a socket opened without this module would be. */
    Page,
}

/**
 * The browser's socket at [url] closed or failed before it opened.
 *
 * @property url The address the socket was opened to.
 * @property code The close event's code, 1006 when the connection ended without a close frame.
 */
class BrowserSocketFailed(val url: String, val code: Int) : Exception("Could not connect to $url (close code $code)")

/**
 * The socket worker couldn't run: the browser has no `Worker`, its script at [script] didn't load
 * (for example the page doesn't serve it), or it threw.
 *
 * @property script The worker script's URL, relative to the page.
 */
class SocketWorkerFailed(val script: String) : Exception("The socket worker $script could not run")
