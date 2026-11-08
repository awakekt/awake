/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Dual packet-count and byte-count accounting guard between a connection's async socket reader and
 * the consumer (such as an authoritative server tick) that drains queued packets.
 *
 * The socket reader runs off the simulation thread so a late or stalled tick never backs frames up
 * into the underlying socket buffer. Before enqueueing each packet, the reader calls [offer]; as the
 * consumer drains each packet, it calls [taken]. If a peer exceeds either [capacity] packets or
 * [byteCapacity] total queued bytes, [offer] rejects the packet and latches [overflowed] permanently
 * for the connection's lifetime so the consumer can drop remaining queued traffic as a flood.
 *
 * @property capacity Maximum number of unconsumed packets allowed in the queue.
 * @property byteCapacity Maximum cumulative byte size of unconsumed packets allowed in the queue.
 */
@OptIn(ExperimentalAtomicApi::class)
class InboundQueue(
    val capacity: Int,
    val byteCapacity: Long,
) {
    init {
        require(capacity >= 0) { "capacity must be non-negative, was $capacity" }
        require(byteCapacity >= 0L) { "byteCapacity must be non-negative, was $byteCapacity" }
    }

    private val held = AtomicInt(0)
    private val heldBytes = AtomicLong(0L)

    /**
     * Whether this connection ever exceeded [capacity] or [byteCapacity], or was explicitly marked
     * via [cutOff]. Once `true`, remains `true` for the rest of the queue's lifetime.
     */
    @Volatile
    var overflowed: Boolean = false
        private set

    /** Number of packets currently reserved via [offer] and not yet released via [taken]. */
    val size: Int
        get() = held.load()

    /** Total byte count currently reserved via [offer] and not yet released via [taken]. */
    val bytes: Long
        get() = heldBytes.load()

    /**
     * Reserves queue capacity for one incoming packet of [bytes] length.
     *
     * @param bytes Byte length of the packet about to be queued (must be non-negative).
     * @return `true` if the reservation succeeded within both bounds, or `false` (latching
     *   [overflowed] without retaining the reservation) if either bound would be exceeded or the
     *   queue already overflowed.
     */
    fun offer(bytes: Int): Boolean {
        require(bytes >= 0) { "bytes must be non-negative, was $bytes" }
        if (overflowed) return false
        val packets = held.addAndFetch(1)
        val total = heldBytes.addAndFetch(bytes.toLong())
        val withinBounds = packets <= capacity && total <= byteCapacity
        if (!withinBounds) {
            taken(bytes)
            overflowed = true
        }
        return withinBounds
    }

    /**
     * Releases the reservation of one previously accepted packet of [bytes] length.
     *
     * @param bytes Byte length passed to the matching successful [offer] call (must be non-negative).
     */
    fun taken(bytes: Int) {
        require(bytes >= 0) { "bytes must be non-negative, was $bytes" }
        held.addAndFetch(-1)
        heldBytes.addAndFetch(-bytes.toLong())
    }

    /** Latches [overflowed] to `true` when an external flood condition (such as [ReadEnd.InboxFull]) is detected. */
    fun cutOff() {
        overflowed = true
    }
}

/** Terminal reason returned when [readEach] stops draining an inbound packet channel. */
enum class ReadEnd {
    /** The peer or local host closed the inbound channel normally. */
    Closed,

    /** The packet consumer callback returned `false` (for example after [InboundQueue.offer] overflowed). */
    Refused,

    /** The underlying transport reported one or more dropped inbound packets before handoff. */
    InboxFull,
}

/**
 * Drains packets from this [ReceiveChannel] in arrival order and passes each to [read] until the
 * channel closes ([ReadEnd.Closed]), [dropped] reports positive transport-level packet loss
 * ([ReadEnd.InboxFull]), or [read] returns `false` ([ReadEnd.Refused]).
 *
 * Any close cause carried by the channel is rethrown. Checking [dropped] before invoking [read]
 * ensures a peer that overflows a `DROP_OLDEST` transport inbox is cut off immediately rather than
 * having silently truncated packet sequences processed.
 *
 * @param dropped Callback returning the transport's cumulative dropped-packet count, or `null` when
 *   the transport does not track drops.
 * @param read Consumer invoked for each packet; returning `false` terminates reading with [ReadEnd.Refused].
 * @return Why reading stopped.
 */
suspend fun ReceiveChannel<ByteArray>.readEach(
    dropped: () -> Long?,
    read: (ByteArray) -> Boolean,
): ReadEnd {
    var outcome: ReadEnd? = null
    while (outcome == null) {
        val next = receiveCatching()
        next.exceptionOrNull()?.let { throw it }
        val packet = next.getOrNull()
        outcome = when {
            packet == null -> ReadEnd.Closed
            (dropped() ?: 0L) > 0L -> ReadEnd.InboxFull
            !read(packet) -> ReadEnd.Refused
            else -> null
        }
    }
    return outcome
}

/**
 * Drains packets from [TransportSession.incoming], consulting [TransportSession.droppedInbound]
 * before handing each packet to [read].
 *
 * @param read Consumer invoked for each packet; returning `false` terminates reading with [ReadEnd.Refused].
 * @return Why reading stopped.
 */
suspend fun TransportSession.readEach(read: (ByteArray) -> Boolean): ReadEnd =
    incoming().readEach(dropped = { droppedInbound }, read = read)
