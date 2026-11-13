/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Stages the packets one tick produces for one client and hands them to [delegate] as one
 * [BatchFrame] per [flush], so the per-send channel, scheduling and framing cost is paid once per
 * client per tick, not once per message.
 *
 * Off unless the peer asked for it: when [enabled] is `false` (a peer that did not connect with
 * [BatchFrame.NEGOTIATION_PARAMETER], or [acceptsBatches] says so) every call goes straight to
 * [delegate] as it does today, [sendLatest] included, and [flush] does nothing. An older client
 * therefore keeps receiving the unbatched frames it understands.
 *
 * - **Order:** staged packets leave in the order they were staged; a batch never reorders them.
 * - **Reliability:** a batch rides [DeliveryChannel.Reliable] when any packet in it was sent
 *   reliable (over a datagram transport that lifts its unreliable packets too, never the reverse);
 *   packets keep their own meaning to the consumer's codec.
 * - **Latest-only lane:** [sendLatest] with a lane replaces the packet already staged for that lane,
 *   so a stale snapshot is dropped ([staleDropped]) rather than queued. The newest takes its own place
 *   in the order, where it was produced.
 * - **Size:** a batch holds at most [maxBatchBytes] (header and length prefixes included); [flush]
 *   splits a larger tick into several batches in order. One packet bigger than the cap goes alone.
 * - **Backpressure:** unchanged in kind. [delegate] sees one send per batch instead of one per
 *   packet, so its outbound queue fills more slowly; a full queue is handled by the delegate as ever.
 *
 * Not thread-safe: call it from the thread that runs the tick.
 *
 * @param delegate The session to write to; everything else ([id], [incoming], [close]) is its own.
 * @property maxBatchBytes Largest batch frame payload in bytes; at least [MIN_BATCH_BYTES].
 * @property enabled Whether to batch; defaults to whether [delegate]'s peer asked for it.
 */
class BatchedSession(
    private val delegate: TransportSession,
    val maxBatchBytes: Int = DEFAULT_BATCH_BYTES,
    val enabled: Boolean = delegate.acceptsBatches,
) : TransportSession by delegate {
    init {
        require(maxBatchBytes >= MIN_BATCH_BYTES) { "maxBatchBytes must be at least $MIN_BATCH_BYTES, was $maxBatchBytes" }
    }

    private val packets = ArrayList<ByteArray>()
    private var lanes = IntArray(INITIAL_SLOTS)
    private var reliable = false

    /** Packets staged and not yet flushed. */
    val staged: Int get() = packets.size

    /** Frames handed to the delegate by [flush]. */
    var framesWritten: Long = 0L
        private set

    /** Packets replaced in a latest-only lane before they were sent. */
    var staleDropped: Long = 0L
        private set

    /** Stages [packet] after those already staged, or sends it at once when not [enabled]. */
    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
        if (!enabled) return delegate.send(channel, packet)
        if (channel == DeliveryChannel.Reliable) reliable = true
        stage(packet, NO_LANE)
    }

    /**
     * Stages [packet] in a latest-only [lane] (any non-negative number): a packet already staged for
     * [lane] is dropped, so only the newest of the tick is sent. Not [enabled]: sent at once,
     * unconflated, as before.
     */
    suspend fun sendLatest(lane: Int, packet: ByteArray) {
        require(lane >= 0) { "lane must be non-negative, was $lane" }
        if (!enabled) return delegate.send(DeliveryChannel.Unreliable, packet)
        val stale = packets.indices.lastOrNull { lanes[it] == lane }
        if (stale != null) {
            packets.removeAt(stale)
            lanes.copyInto(lanes, stale, stale + 1, packets.size + 1)
            staleDropped++
        }
        stage(packet, lane)
    }

    /** Writes everything staged as one batch, or as several in order when it exceeds [maxBatchBytes]. */
    suspend fun flush() {
        if (packets.isEmpty()) return
        val channel = if (reliable) DeliveryChannel.Reliable else DeliveryChannel.Unreliable
        var from = 0
        while (from < packets.size) {
            var to = from
            var size = BatchFrame.HEADER_BYTES + BatchFrame.varIntSize(packets.size - from)
            while (to < packets.size && (to == from || size + BatchFrame.entrySize(packets[to].size) <= maxBatchBytes)) {
                size += BatchFrame.entrySize(packets[to].size)
                to++
            }
            delegate.send(channel, BatchFrame.encode(packets, from, to))
            framesWritten++
            from = to
        }
        packets.clear()
        reliable = false
    }

    private fun stage(packet: ByteArray, lane: Int) {
        if (lanes.size <= packets.size) lanes = lanes.copyOf(lanes.size * 2)
        lanes[packets.size] = lane
        packets += packet
    }

    /** Defaults and limits of [BatchedSession]. */
    companion object {
        /** Default cap on one batch frame payload (16 KiB, well under the server's 64 KiB frame limit). */
        const val DEFAULT_BATCH_BYTES: Int = 16 * 1024

        /** Smallest allowed cap, room for the header, a count and a short packet. */
        const val MIN_BATCH_BYTES: Int = 16

        private const val NO_LANE = -1
        private const val INITIAL_SLOTS = 32
    }
}
