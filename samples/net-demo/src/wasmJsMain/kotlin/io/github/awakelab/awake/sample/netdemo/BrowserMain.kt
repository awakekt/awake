/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.Element

private const val DEFAULT_SERVER_PORT = 9_540
private const val HALF_TURN = 3.14159f

/**
 * The browser half of the demo. Everything below the socket -- protocol, codec, grid -- is the
 * same common code the desktop client runs; only the entry point and the output element differ.
 *
 * This is the payoff for choosing WebSocket first: a browser has no UDP at all, so any
 * transport that reaches this page has to be WebSocket or WebTransport, and Ktor has no QUIC
 * (docs/plans/network.md).
 */
fun main() {
    val status = requireElement("status")
    val grid = requireElement("grid")
    val port = window.location.searchParam("port")?.toIntOrNull() ?: DEFAULT_SERVER_PORT
    val name = window.location.searchParam("name") ?: "browser"
    val secure = window.location.searchParam("tls") == "1"
    val scheme = if (secure) "wss" else "ws"

    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        val client = NetDemoClient(
            KtorWebSocketClient(host = window.location.hostname, port = port, secure = secure),
        )
        status.textContent = "connecting to $scheme://${window.location.hostname}:$port/net ..."
        runCatching { client.connect(name) }.onFailure {
            // A wss:// failure against a self-signed certificate looks identical to the server
            // being down, because the browser refuses to say more. Name both causes.
            val hint = if (secure) {
                "Either the server is not running, or its certificate is not trusted -- " +
                    "open https://${window.location.hostname}:$port once and accept it."
            } else {
                "Is the desktop server running?"
            }
            status.textContent = "connect failed: ${it.message}. $hint"
            return@launch
        }
        client.receiveUntil { it.netId != null }
        val self = client.netId
        status.textContent = "joined as $name, netId=${self?.value}"

        launch {
            val orbit = OrbitInput(phaseOffset = if (name.hashCode() % 2 == 0) 0f else HALF_TURN)
            val periodMillis = 1_000L / OrbitInput.SEND_HZ
            while (true) {
                val (moveX, moveY) = orbit.advance()
                client.sendInput(orbit.tick, moveX, moveY)
                delay(periodMillis)
            }
        }

        client.receiveLoop {
            status.textContent = GridView.status(it.snapshot)
            grid.textContent = GridView.render(it.interpolated(), self)
        }
        status.textContent = "disconnected"
    }
}

private fun requireElement(id: String): Element =
    requireNotNull(document.getElementById(id)) { "index.html is missing #$id" }

/** `URLSearchParams` without pulling in a wrapper for two reads. */
private fun org.w3c.dom.Location.searchParam(key: String): String? =
    search.removePrefix("?")
        .split("&")
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotEmpty() }
