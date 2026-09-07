/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.server

import kotlinx.coroutines.CompletableDeferred
import java.net.BindException
import java.util.concurrent.ConcurrentLinkedQueue

/** Default TCP port used for Awake remote debug control WebSockets (`42770`). */
const val AWAKE_DEBUG_CONTROL_PORT = 42770

/**
 * Service handling incoming remote debug control commands and returning state snapshots.
 *
 * @param TCommand The command model type.
 * @param TSnapshot The snapshot response state model type.
 */
interface DebugService<TCommand, TSnapshot> {
    /** Processes an incoming [command]. */
    fun handle(command: TCommand)

    /** Returns a current state [TSnapshot] after command execution. */
    fun snapshot(): TSnapshot
}

/**
 * Transport contract receiving remote commands and completing response deferreds.
 */
interface DebugTransport<TCommand, TSnapshot> {
    /** Starts the underlying transport listener. */
    fun start()

    /** Drains all pending commands queued since the last frame. */
    fun drainCommands(): List<Pair<TCommand, CompletableDeferred<TSnapshot>>>

    /** Stops the transport and closes active listener sockets. */
    fun stop()
}

/**
 * Event loop orchestrating transport command draining and service state execution.
 */
class DebugServiceLoop<TCommand, TSnapshot>(
    private val transport: DebugTransport<TCommand, TSnapshot>,
    private val service: DebugService<TCommand, TSnapshot>,
) {
    /** Starts the debug service loop transport. */
    fun start() {
        transport.start()
    }

    /** Processes pending transport commands before each engine frame step. */
    fun beforeFrame() {
        transport.drainCommands().forEach { (command, deferred) ->
            service.handle(command)
            deferred.complete(service.snapshot())
        }
    }

    /** Stops the debug service loop transport. */
    fun stop() {
        transport.stop()
    }
}

/**
 * Constructs a [DebugServiceLoop] backed by a WebSocket transport.
 */
fun <TCommand, TSnapshot> webSocketDebugLoop(
    port: Int = AWAKE_DEBUG_CONTROL_PORT,
    parseCommand: (String) -> TCommand?,
    encodeResponse: (TSnapshot) -> String,
    service: DebugService<TCommand, TSnapshot>,
): DebugServiceLoop<TCommand, TSnapshot> = DebugServiceLoop(
    transport = WebSocketDebugTransport(
        port = port,
        parseCommand = parseCommand,
        encodeResponse = encodeResponse,
    ),
    service = service,
)

/**
 * Executes an engine loop wrapped with an optional remote [DebugServiceLoop].
 */
fun <TCommand, TSnapshot> withOptionalDebugLoop(
    enabled: Boolean,
    createLoop: () -> DebugServiceLoop<TCommand, TSnapshot>,
    run: (beforeFrame: () -> Unit, afterLoop: () -> Unit) -> Unit,
) {
    var loop = if (enabled) createLoop() else null
    var stopped = false

    val stopLoop: () -> Unit = {
        if (!stopped) {
            stopped = true
            loop?.stop()
        }
    }

    if (loop != null) {
        try {
            loop.start()
        } catch (error: BindException) {
            val port = error.message
                ?.substringAfterLast(':')
                ?.trim()
                ?.toIntOrNull()
                ?: AWAKE_DEBUG_CONTROL_PORT
            System.err.println("Awake debug controls disabled: port $port is already in use.")
            loop = null
        }
    }

    try {
        run({ loop?.beforeFrame() }, stopLoop)
    } catch (t: Throwable) {
        stopLoop()
        throw t
    }
}

internal class RecordingDebugTransport<TCommand, TSnapshot> : DebugTransport<TCommand, TSnapshot> {
    private val queue = ConcurrentLinkedQueue<Pair<TCommand, CompletableDeferred<TSnapshot>>>()
    var started = false
        private set
    var stopped = false
        private set

    override fun start() {
        started = true
    }

    override fun drainCommands(): List<Pair<TCommand, CompletableDeferred<TSnapshot>>> {
        val drained = mutableListOf<Pair<TCommand, CompletableDeferred<TSnapshot>>>()
        while (true) {
            drained += queue.poll() ?: break
        }
        return drained
    }

    override fun stop() {
        stopped = true
    }

    fun enqueue(command: TCommand): CompletableDeferred<TSnapshot> {
        val deferred = CompletableDeferred<TSnapshot>()
        queue += command to deferred
        return deferred
    }
}
