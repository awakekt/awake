/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.logging

/**
 * The most recent [capacity] records, for a console to read.
 *
 * Bounded, because a buffer that keeps everything is a leak in any session long enough to want a
 * log. Oldest is overwritten.
 *
 * [droppedCount] is not optional decoration. A ring that silently discards makes whatever displays
 * it claim a completeness it does not have, which is the same failure as `phaseStats()` reporting
 * zeros for phases it never measured -- the reader cannot tell "nothing happened" from "I stopped
 * telling you". A console showing "1 error" is wrong in a way that matters if forty scrolled off.
 *
 * A fixed array with a write cursor, not an ArrayDeque: this is written from the frame loop, and
 * the point of a ring is that a steady state allocates nothing at all.
 *
 * Single-writer, like [Log] -- see its note on why that is a checked assumption rather than a hope.
 */
class LogRingBuffer(val capacity: Int = DEFAULT_CAPACITY) : LogSink {
    init {
        require(capacity > 0) { "A log ring buffer must hold at least one record." }
    }

    private val records = arrayOfNulls<LogRecord>(capacity)

    /** Where the next record goes. Wraps. */
    private var cursor = 0

    /** How many slots hold a record. Stops climbing once the ring is full. */
    private var filled = 0

    /** Records overwritten because the ring was full. */
    var droppedCount: Long = 0
        private set

    val size: Int get() = filled

    /** How many of the retained records are at [level] or worse -- the console's badge counts. */
    fun countAtLeast(level: LogLevel): Int {
        var count = 0
        forEach { if (it.level >= level) count++ }
        return count
    }

    override fun emit(record: LogRecord) {
        if (filled == capacity) droppedCount++
        records[cursor] = record
        cursor = (cursor + 1) % capacity
        if (filled < capacity) filled++
    }

    /**
     * Visits retained records oldest first, without copying them.
     *
     * The read path a per-frame consumer should use. [snapshot] exists for a caller that needs a
     * list it can hold; this one is for the console, which rebuilds its rows every frame and would
     * otherwise allocate the whole buffer to do it.
     */
    inline fun forEach(action: (LogRecord) -> Unit) {
        for (index in 0 until size) action(get(index))
    }

    /** The record [index] places after the oldest retained one. */
    operator fun get(index: Int): LogRecord {
        require(index in 0 until filled) { "Index $index is outside the $filled retained records." }
        val start = if (filled == capacity) cursor else 0
        return checkNotNull(records[(start + index) % capacity])
    }

    /** Retained records, oldest first, copied. */
    fun snapshot(): List<LogRecord> = ArrayList<LogRecord>(filled).also { out ->
        forEach { out.add(it) }
    }

    /**
     * Empties the ring.
     *
     * [droppedCount] resets too: it counts what this buffer discarded from what it was asked to
     * hold, and after a clear it was asked to hold nothing. A survivor count carried across a
     * "Clear" would report drops for records the user deliberately threw away.
     */
    fun clear() {
        records.fill(null)
        cursor = 0
        filled = 0
        droppedCount = 0
    }

    companion object {
        /** Enough to cover a startup sequence and a few seconds of frames without being a leak. */
        const val DEFAULT_CAPACITY = 2048
    }
}
