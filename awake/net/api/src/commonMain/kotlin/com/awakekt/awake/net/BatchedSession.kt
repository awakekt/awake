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
 * - **Allocation:** staging copies each packet into one buffer the session keeps and reuses (it grows
 *   only when a tick needs more than any before it, and a flush resets it), so staging, a latest-only
 *   replacement and a reset allocate nothing, and the caller may reuse its array at once. A flush
 *   allocates the one exactly sized array it hands to [delegate]: [TransportSession.send] takes
 *   ownership of its array and may write it later (a channel to a writer coroutine), so the staging
 *   buffer cannot be lent to it without a data race.
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

    // Entries laid out back to back as `varint length | bytes`; starts[i] is where entry i begins.
    private var entries = ByteArray(INITIAL_BYTES)
    private var used = 0
    private var starts = IntArray(INITIAL_SLOTS)
    private var lanes = IntArray(INITIAL_SLOTS)
    private var count = 0
    private var reliable = false

    /** Packets staged and not yet flushed. */
    val staged: Int get() = count

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
        stage(packet, 0, packet.size, NO_LANE)
    }

    /**
     * Stages `packet[offset until offset + length]`, so a caller can encode into one array it reuses.
     * Not [enabled]: the range is copied out and sent at once.
     */
    suspend fun send(channel: DeliveryChannel, packet: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= packet.size) { "range is outside the packet" }
        if (!enabled) return delegate.send(channel, packet.copyOfRange(offset, offset + length))
        if (channel == DeliveryChannel.Reliable) reliable = true
        stage(packet, offset, length, NO_LANE)
    }

    /**
     * Stages [packet] in a latest-only [lane] (any non-negative number): a packet already staged for
     * [lane] is dropped, so only the newest of the tick is sent. Not [enabled]: sent at once,
     * unconflated, as before.
     */
    suspend fun sendLatest(lane: Int, packet: ByteArray) = sendLatest(lane, packet, 0, packet.size)

    /** [sendLatest] for `packet[offset until offset + length]`. */
    suspend fun sendLatest(lane: Int, packet: ByteArray, offset: Int, length: Int) {
        require(lane >= 0) { "lane must be non-negative, was $lane" }
        require(offset >= 0 && length >= 0 && offset + length <= packet.size) { "range is outside the packet" }
        if (!enabled) {
            val whole = offset == 0 && length == packet.size
            return delegate.send(DeliveryChannel.Unreliable, if (whole) packet else packet.copyOfRange(offset, offset + length))
        }
        dropStale(lane)
        stage(packet, offset, length, lane)
    }

    /** Writes everything staged as one batch, or as several in order when it exceeds [maxBatchBytes]. */
    suspend fun flush() {
        if (count == 0) return
        val channel = if (reliable) DeliveryChannel.Reliable else DeliveryChannel.Unreliable
        var from = 0
        while (from < count) {
            var to = from + 1
            while (to < count && fits(from, to + 1)) to++
            delegate.send(channel, BatchFrame.frameEntries(entries, starts[from], endOf(to - 1), to - from))
            framesWritten++
            from = to
        }
        used = 0
        count = 0
        reliable = false
    }

    private fun endOf(entry: Int): Int = if (entry + 1 < count) starts[entry + 1] else used

    /** Whether entries `from until to` form a batch within [maxBatchBytes]; one entry always does. */
    private fun fits(from: Int, to: Int): Boolean =
        BatchFrame.HEADER_BYTES + BatchFrame.varIntSize(to - from) + (endOf(to - 1) - starts[from]) <= maxBatchBytes

    private fun dropStale(lane: Int) {
        var i = count - 1
        while (i >= 0 && lanes[i] != lane) i--
        if (i < 0) return
        val start = starts[i]
        val end = endOf(i)
        entries.copyInto(entries, start, end, used)
        used -= end - start
        starts.copyInto(starts, i, i + 1, count)
        lanes.copyInto(lanes, i, i + 1, count)
        for (j in i until count - 1) starts[j] -= end - start
        count--
        staleDropped++
    }

    private fun stage(packet: ByteArray, offset: Int, length: Int, lane: Int) {
        val need = BatchFrame.entrySize(length)
        if (used + need > entries.size) entries = entries.copyOf(maxOf(entries.size * 2, used + need))
        if (count == starts.size) {
            starts = starts.copyOf(count * 2)
            lanes = lanes.copyOf(count * 2)
        }
        starts[count] = used
        lanes[count] = lane
        count++
        used = BatchFrame.writeVarInt(entries, used, length)
        packet.copyInto(entries, used, offset, offset + length)
        used += length
    }

    /** Defaults and limits of [BatchedSession]. */
    companion object {
        /** Default cap on one batch frame payload (16 KiB, well under the server's 64 KiB frame limit). */
        const val DEFAULT_BATCH_BYTES: Int = 16 * 1024

        /** Smallest allowed cap, room for the header, a count and a short packet. */
        const val MIN_BATCH_BYTES: Int = 16

        private const val NO_LANE = -1
        private const val INITIAL_SLOTS = 32
        private const val INITIAL_BYTES = 2048
    }
}
