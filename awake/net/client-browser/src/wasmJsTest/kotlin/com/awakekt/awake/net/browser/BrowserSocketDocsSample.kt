/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.browser

import com.awakekt.awake.net.DirectTransport

// The connect samples in the web networking guide (`website/docs/guides/web-networking.md`). They
// compile with the wasmJs tests; they don't run, since they need a server.

// --8<-- [start:connect]
// Connects to the game server, its socket in a Web Worker where the page can run one.
suspend fun connectToServer(): DirectTransport {
    val transport = connectBrowserSocket("wss://play.example.com/net")
    // False when the browser has no Worker, or the page doesn't serve awake-net/socket-worker.js.
    println("socket in a worker: ${transport.runsInWorker}")
    return transport
}
// --8<-- [end:connect]

// --8<-- [start:modes]
// Runs the socket where a launch option says: "page" keeps it on the page's thread.
suspend fun connectWith(option: String?): BrowserSocketTransport = connectBrowserSocket(
    url = "wss://play.example.com/net",
    mode = if (option == "page") BrowserSocketMode.Page else BrowserSocketMode.Auto,
)
// --8<-- [end:modes]
