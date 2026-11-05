/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import com.awakekt.awake.core.logging.Logger
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Where the page finds the socket worker's script by default, relative to the page: the path the
 * `com.awakekt.awake.plugin.socket-worker` Gradle plugin puts it at, beside `index.html`.
 */
const val SOCKET_WORKER_SCRIPT: String = "awake-net/socket-worker.js"

/** Whether the page can run a dedicated `Worker`, which a socket in a worker needs. */
fun socketWorkerSupported(): Boolean = workerDefined()

/**
 * Connects to the server at [url] (`ws:` or `wss:`) over the browser's own `WebSocket`, binary
 * messages only, one message per packet, and returns the connection once the socket has opened.
 *
 * [mode] picks where the socket runs (see [BrowserSocketTransport]). [BrowserSocketMode.Auto], the
 * default, runs it in a dedicated worker from [workerScript], so a frame that holds the page's thread
 * delays neither when a reply is stamped nor what `bufferedAmount` reads, and falls back to the page,
 * saying why once, when the browser has no `Worker` or the script doesn't run (for example the page
 * doesn't serve it). It fails with [BrowserSocketFailed] when the socket closes or fails before it
 * opens, and in [BrowserSocketMode.Worker] with [SocketWorkerFailed] when the worker can't run.
 * Cancelling it closes the socket and ends the worker.
 *
 * While [holdWakeLock] is true, the open connection holds a shared Web Lock (`navigator.locks`) so the
 * browser is less likely to freeze a backgrounded tab while the socket stays connected, releasing it
 * as soon as the connection closes; where `navigator.locks` is unavailable or refuses, it is a no-op.
 *
 * Call it on the page's thread, and use the connection there.
 */
suspend fun connectBrowserSocket(
    url: String,
    mode: BrowserSocketMode = BrowserSocketMode.Auto,
    workerScript: String = SOCKET_WORKER_SCRIPT,
    holdWakeLock: Boolean = true,
): BrowserSocketTransport = when (mode) {
    BrowserSocketMode.Page -> connectOnPage(url, holdWakeLock)
    BrowserSocketMode.Worker -> connectInWorker(url, workerScript, holdWakeLock)
    BrowserSocketMode.Auto -> if (socketWorkerSupported()) {
        try {
            connectInWorker(url, workerScript, holdWakeLock)
        } catch (failed: SocketWorkerFailed) {
            fallingBack { "${failed.message}, so the socket runs on the page; serve it with the socket-worker Gradle plugin" }
            connectOnPage(url, holdWakeLock)
        }
    } else {
        fallingBack { "This browser has no Worker, so the socket runs on the page" }
        connectOnPage(url, holdWakeLock)
    }
}

// Binary-compatible entry point for callers compiled before holdWakeLock was added.
@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
suspend fun connectBrowserSocket(
    url: String,
    mode: BrowserSocketMode = BrowserSocketMode.Auto,
    workerScript: String = SOCKET_WORKER_SCRIPT,
): BrowserSocketTransport = connectBrowserSocket(
    url = url,
    mode = mode,
    workerScript = workerScript,
    holdWakeLock = true,
)

private val log = Logger("net-client-browser")
private var saidWhy = false

/** Says once per page why a socket runs on the page instead of in a worker. */
private inline fun fallingBack(why: () -> String) {
    if (saidWhy) return
    saidWhy = true
    log.warn(why)
}

/** WebSocket's close code for a connection that ended without a close frame. */
internal const val ABNORMAL_CLOSE = 1006

@JsFun("() => typeof Worker !== 'undefined'")
internal external fun workerDefined(): Boolean

/** Milliseconds since the Unix epoch on the page's high-resolution clock, the base the worker stamps messages on. */
@JsFun("() => performance.timeOrigin + performance.now()")
internal external fun epochMillis(): Double

@JsFun("(ms, f) => { setTimeout(() => f(), ms); }")
internal external fun setTimeout(millis: Int, action: () -> Unit)
