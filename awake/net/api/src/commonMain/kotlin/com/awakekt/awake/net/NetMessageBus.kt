/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Protocol-agnostic, reactive message bus bridging game network transport loops with Clean
 * Architecture `RemoteDataSource` and `Repository` layers via Kotlin Coroutines and [Flow].
 *
 * Keeps request/response and push-state features (shops, mail, collections, crafting, guild
 * panels, and similar UI-driven workflows) decoupled from ECS components and 60 Hz polling loops:
 * - Remote data sources call [send], collect typed server pushes via [incoming], or issue
 *   subscribe-before-send RPCs with [request].
 * - The transport pump drains queued outbound messages with zero steady-state allocations via
 *   [drainOutbound] and feeds decoded inbound packets via [deliver], or plugs straight into
 *   [ClientNetFeatures] via [asClientNetFeature].
 * - Closing the bus idempotently drains and closes its outbound queue and marks [connected] false.
 *
 * @param Out Base type of outbound client messages.
 * @param In Base type of inbound server messages.
 * @param outboundCapacity Bounded capacity of queued outbound messages waiting for [drainOutbound].
 * @param inboundCapacity Extra buffer capacity of the inbound [SharedFlow] for bursty server pushes.
 */
@OptIn(ExperimentalAtomicApi::class)
class NetMessageBus<Out : Any, In : Any>(
    outboundCapacity: Int = DEFAULT_OUTBOUND_CAPACITY,
    inboundCapacity: Int = DEFAULT_INBOUND_CAPACITY,
) : AutoCloseable {
    init {
        require(outboundCapacity > 0) { "outboundCapacity must be positive, got $outboundCapacity." }
        require(inboundCapacity > 0) { "inboundCapacity must be positive, got $inboundCapacity." }
    }

    private val outbound = Channel<Out>(capacity = outboundCapacity)
    private val inbound = MutableSharedFlow<In>(
        replay = 0,
        extraBufferCapacity = inboundCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _connected = MutableStateFlow(false)
    private val _resetEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = RESET_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val closed = AtomicBoolean(false)

    /** Whether the underlying client session is currently connected. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Emits whenever the client world resets (zone change or resync) while the session stays up. */
    val resetEvents: SharedFlow<Unit> = _resetEvents.asSharedFlow()

    /** Whether [close] has been invoked on this bus. */
    val isClosed: Boolean get() = closed.load()

    /**
     * Enqueues [message] for transmission on the next [drainOutbound] pump pass.
     *
     * @param message Outbound message to queue.
     * @return `true` if queued, or `false` if this bus is closed or its outbound buffer is full.
     */
    fun send(message: Out): Boolean {
        if (closed.load()) return false
        return outbound.trySend(message).isSuccess
    }

    /**
     * Returns a cold [Flow] emitting every inbound message delivered to this bus that is an
     * instance of [messageClass].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : In> incoming(messageClass: KClass<T>): Flow<T> =
        inbound.filter { messageClass.isInstance(it) }.map { it as T }

    /**
     * Returns a cold [Flow] emitting every inbound message of type [T] delivered to this bus.
     */
    inline fun <reified T : In> incoming(): Flow<T> = incoming(T::class)

    /**
     * Sends [message] and suspends until a delivered inbound message of [responseClass] satisfies
     * [match], or throws [kotlinx.coroutines.TimeoutCancellationException] after [timeout].
     *
     * Subscribes to the inbound stream eagerly ([CoroutineStart.UNDISPATCHED]) before calling
     * [send], eliminating subscribe-vs-send races on loopback or low-latency transports.
     *
     * @param Resp Expected inbound response type.
     * @param message Outbound request message.
     * @param responseClass Expected inbound response type.
     * @param timeout Maximum wait duration before timing out.
     * @param match Predicate selecting the matching response (for example by correlation ID).
     * @return The first matching delivered response.
     */
    suspend fun <Resp : In> request(
        message: Out,
        responseClass: KClass<Resp>,
        timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
        match: (Resp) -> Boolean = { true },
    ): Resp = coroutineScope {
        val reply = CompletableDeferred<Resp>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            inbound.collect { candidate ->
                if (responseClass.isInstance(candidate)) {
                    @Suppress("UNCHECKED_CAST")
                    val typed = candidate as Resp
                    if (match(typed)) reply.complete(typed)
                }
            }
        }
        try {
            check(send(message)) {
                "NetMessageBus could not enqueue outbound message '${message::class.simpleName}'."
            }
            withTimeout(timeout) { reply.await() }
        } finally {
            collector.cancel()
        }
    }

    /**
     * Sends [message] and suspends until a matching inbound [Resp] arrives within [timeout].
     */
    suspend inline fun <reified Resp : In> request(
        message: Out,
        timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
        noinline match: (Resp) -> Boolean = { true },
    ): Resp = request(message, Resp::class, timeout, match)

    /**
     * Drains every currently queued outbound message into [sink] without allocating when empty.
     *
     * @param sink Transport writer invoked for each queued message in FIFO order.
     * @return Number of messages drained.
     */
    fun drainOutbound(sink: (Out) -> Unit): Int {
        var count = 0
        while (true) {
            val next = outbound.tryReceive().getOrNull() ?: break
            sink(next)
            count++
        }
        return count
    }

    /**
     * Delivers a decoded inbound [message] from the transport layer to all active [incoming] and
     * [request] collectors.
     *
     * @param message Decoded server message.
     * @return `true` if emitted into the inbound stream, or `false` if this bus is closed.
     */
    fun deliver(message: In): Boolean {
        if (closed.load()) return false
        return inbound.tryEmit(message)
    }

    /** Marks the bus connected when the underlying client session enters the active state. */
    fun onConnected() {
        if (!closed.load()) _connected.value = true
    }

    /** Marks the bus disconnected when the underlying client session closes or drops. */
    fun onDisconnected() {
        _connected.value = false
    }

    /** Notifies listeners that the replicated world reset while the session remained connected. */
    fun onReset() {
        if (!closed.load()) _resetEvents.tryEmit(Unit)
    }

    /**
     * Idempotently marks the bus closed, drops queued outbound messages, and updates [connected]
     * to `false`.
     */
    override fun close() {
        if (!closed.compareAndSet(expectedValue = false, newValue = true)) return
        _connected.value = false
        while (outbound.tryReceive().isSuccess) {
            // Drain retained outbound payloads so closed sessions release references promptly.
        }
        outbound.close()
    }

    /** Default buffer sizes and RPC timeout constants for [NetMessageBus]. */
    companion object {
        /** Default bounded FIFO capacity for queued outbound messages. */
        const val DEFAULT_OUTBOUND_CAPACITY: Int = 256

        /** Default extra replay-free buffer capacity for inbound server push bursts. */
        const val DEFAULT_INBOUND_CAPACITY: Int = 256

        /** Default request-response wait budget for [request]. */
        val DEFAULT_REQUEST_TIMEOUT: Duration = 15.seconds

        private const val RESET_BUFFER_CAPACITY: Int = 8
    }
}

/**
 * Adapts this [NetMessageBus] into a [ClientNetFeature] that routes every inbound message type in
 * [inboundTypes] into [NetMessageBus.deliver], flushes outbound messages via [sendMessage], and
 * forwards session lifecycle callbacks.
 *
 * @param C Client network context type passed to [ClientNetFeature] callbacks.
 * @param Out Base type of outbound client messages.
 * @param In Base type of inbound server messages.
 * @param id Unique feature ID within [ClientNetFeatures].
 * @param inboundTypes Inbound message classes owned by this bus feature.
 * @param sendMessage Callback sending one drained outbound message through [C].
 * @param flushOutbound Whether this feature drains the bus's outbound queue (set `false` when
 *   attaching a second inbound-only feature to an already-flushed shared bus).
 * @return A [ClientNetFeature] bridged to this bus.
 */
fun <C, Out : Any, In : Any> NetMessageBus<Out, In>.asClientNetFeature(
    id: String,
    inboundTypes: Collection<KClass<out In>>,
    sendMessage: (C, Out) -> Unit,
    flushOutbound: Boolean = true,
): ClientNetFeature<C, In> = clientNetFeature(id) {
    for (type in inboundTypes) {
        registerInboundDelivery(type, this@asClientNetFeature)
    }
    if (flushOutbound) {
        flushOutbound { ctx -> drainOutbound { sendMessage(ctx, it) } }
    }
    onConnected { onConnected() }
    onDisconnected { onDisconnected() }
    onReset { onReset() }
}

@Suppress("UNCHECKED_CAST")
private fun <C, In : Any> ClientNetFeatureScope<C, In>.registerInboundDelivery(
    type: KClass<out In>,
    bus: NetMessageBus<*, In>,
) {
    on(type as KClass<In>) { _, message -> bus.deliver(message) }
}
