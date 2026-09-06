/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val DEFAULT_PORT = 9_540

private val USAGE = """
    Usage:
      net-demo server [port] [--tls]           start the authoritative server
      net-demo client [port] [name] [--tls]    terminal client that walks in a circle

    --tls serves wss:// with a self-signed certificate generated under build/dev-tls.
    The terminal client trusts that certificate specifically. A browser will not: visit
    https://localhost:<port> once and accept the warning first, or use mkcert.

    The browser client is the same code on wasmJs:
      ./gradlew :samples:net-demo:wasmJsBrowserDevelopmentRun   (serves on 8088)
""".trimIndent()

fun main(args: Array<String>) {
    val secure = args.contains("--tls")
    val positional = args.filterNot { it.startsWith("--") }
    when (positional.firstOrNull()) {
        "server" -> runServer(positional.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT, secure)
        "client" -> runClient(
            port = positional.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT,
            name = positional.getOrNull(2) ?: "player",
            secure = secure,
        )

        else -> println(USAGE)
    }
}

private fun runServer(port: Int, secure: Boolean): Unit = runBlocking {
    val server = NetDemoServer(KtorWebSocketServer(requestedPort = port, secure = secure))
    server.start()
    val scheme = if (secure) "wss" else "ws"
    println("net-demo server listening on $scheme://localhost:${server.port}/net; ctrl-c to stop")
    if (secure) println("self-signed certificate: ${DevTls.keyStoreFile.absolutePath}")
    delay(Long.MAX_VALUE)
}

private fun runClient(port: Int, name: String, secure: Boolean): Unit = runBlocking {
    val client = NetDemoClient(KtorWebSocketClient(port = port, secure = secure))
    client.connect(name)
    client.receiveUntil { it.netId != null }
    val self = client.netId
    println("joined as $name, netId=${self?.value}")

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
        // ANSI home + clear, so the grid animates in place instead of scrolling away.
        print("[H[2J ${GridView.status(it.snapshot)}\n${GridView.render(it.interpolated(), self)}\n")
    }
}

private const val HALF_TURN = 3.14159f
