/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.math2d.Rectangle

/**
 * Retained storage for the backend-neutral result of UI draw-run staging.
 *
 * UI composition still emits a fresh command list every frame, but most contiguous spans in a
 * scene are unchanged. Keeping their staged arrays avoids repeating tessellation and array
 * materialisation on the frame-critical path. The cache belongs to one renderer: staged texture
 * runs retain renderer-owned material handles and must not outlive that renderer.
 *
 * Every span the frame being drawn uses is kept, however many there are, up to a hard bound of 64 x `capacity` (16,384 spans by default);
 * `capacity` bounds only the spans no frame uses any more. A fixed bound on all spans made a UI
 * with more spans than it evict each span before the next frame reached it, re-staging and
 * re-uploading them forever (#1545). Past that bound nothing live is evicted either: new
 * spans are simply not stored, so the spans that fit stay cached and only the excess re-stages.
 * Memory is the live spans' staged runs plus at most `capacity` stale ones; a UI that shrinks
 * releases the rest at the next [beginFrame].
 *
 * A cache hit is only valid while the returned [StagedDrawRun]s remain read-only. Uploaders consume
 * their arrays but do not mutate them; that is the contract that makes sharing them across frames
 * safe.
 */
class RetainedDrawRunCache(
    internal val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Entry(
        val maxQuadsPerRun: Int,
        val primitives: List<DrawCommand>,
        val pathClips: List<DrawPath>,
        val safeInteriorRect: Rectangle?,
        val runs: List<StagedDrawRun>,
        var frame: Long,
    ) {
        /** The next span with the same hash: draw commands' hashes collide, in grids especially. */
        var next: Entry? = null

        fun matches(primitives: List<DrawCommand>, from: Int, to: Int, maxQuads: Int, clips: List<DrawPath>, safe: Rectangle?) =
            maxQuadsPerRun == maxQuads && safeInteriorRect == safe && pathClips.sameAs(clips) &&
                this.primitives.retainedEquals(primitives, from, to)
    }

    /** A hash map key that a lookup can reuse, so finding a span boxes nothing. */
    private class Key(var hash: Int) {
        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean = other is Key && other.hash == hash
    }

    /** Chains of spans by retained hash, oldest hash first. */
    private val entries = LinkedHashMap<Key, Entry>()

    /** Spans held across every chain. */
    private var count = 0
    private val probe = Key(0)

    /** The frame [beginFrame] opened, or 0 while the caller opens none: the cache is then LRU-bounded by [capacity]. */
    private var frame = 0L

    /** Entries the current frame has found or stored. */
    private var live = 0

    /** The most spans held, live or not; see the class comment. */
    internal val maxCapacity: Int = capacity * MAX_LIVE_SPANS_PER_CAPACITY

    /** Retained spans held now, for tests and probes. */
    internal val size: Int get() = count

    /** The hash of the span the last [find] missed, so a miss can be recognised next frame without
     * hashing it again; see [StableSpanTracker]. */
    internal var lastMissHash: Int = 0
        private set

    /**
     * Closes the frame and opens the next: the spans the closed frame did not use are dropped,
     * oldest first, down to [capacity]. One pass per frame, so stores never scan.
     */
    internal fun beginFrame() {
        if (count > capacity) dropStale(count - capacity)
        frame += 1
        live = 0
    }

    /** Whether [store] would keep a new span now, rather than refuse it at [maxCapacity]. */
    internal fun canStore(): Boolean = frame == 0L || count < maxCapacity || live < count

    /** Returns a retained span and marks it used in this frame, or `null` on a miss. */
    fun find(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath> = emptyList(),
        safeInteriorRect: Rectangle? = null,
    ): List<StagedDrawRun>? = find(primitives, 0, primitives.size, maxQuadsPerRun, pathClips, safeInteriorRect)

    /** [find] for `primitives[from until to]`, with no sub-list: a hit allocates nothing. */
    internal fun find(
        primitives: List<DrawCommand>,
        from: Int,
        to: Int,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath>,
        safeInteriorRect: Rectangle?,
    ): List<StagedDrawRun>? {
        val hash = retainedHash(primitives.retainedHashCode(from, to), pathClips, safeInteriorRect)
        probe.hash = hash
        var entry = entries[probe]
        while (entry != null && !entry.matches(primitives, from, to, maxQuadsPerRun, pathClips, safeInteriorRect)) entry = entry.next
        if (entry == null) {
            lastMissHash = hash
            return null
        }
        markLive(entry)
        return entry.runs
    }

    /** Retains a copy of the command values and the staged runs produced for that span. */
    fun store(
        primitives: List<DrawCommand>,
        maxQuadsPerRun: Int,
        pathClips: List<DrawPath> = emptyList(),
        safeInteriorRect: Rectangle? = null,
        runs: List<StagedDrawRun>,
    ) {
        if (capacity == 0 || primitives.isEmpty() || !canStore()) return
        val snapshot = primitives.map(DrawCommand::retentionSnapshot)
        val clipSnapshot = pathClips.map { it.copy(commands = it.commands.toList()) }
        val key = Key(retainedHash(snapshot.retainedHashCode(), clipSnapshot, safeInteriorRect))
        unlink(key) { it.matches(snapshot, 0, snapshot.size, maxQuadsPerRun, clipSnapshot, safeInteriorRect) }
        if (count >= maxCapacity) dropStale(1)
        val entry = Entry(maxQuadsPerRun, snapshot, clipSnapshot, safeInteriorRect, runs.toList(), frame = -1)
        entry.next = entries[key]
        entries[key] = entry
        count += 1
        markLive(entry)
        if (frame == 0L) while (count > capacity) dropOldestChain()
    }

    /** Clears all retained entries from the cache. */
    fun clear() {
        entries.clear()
        count = 0
        live = 0
    }

    private fun markLive(entry: Entry) {
        if (entry.frame != frame) {
            entry.frame = frame
            live += 1
        }
    }

    /** Drops up to [excess] spans the current frame has not used, oldest hash first. One pass. */
    private fun dropStale(excess: Int) {
        var left = excess
        val chains = entries.entries.iterator()
        while (left > 0 && chains.hasNext()) {
            val chain = chains.next()
            var kept: Entry? = null
            var node: Entry? = chain.value
            while (node != null) {
                val next = node.next
                if (left > 0 && node.frame != frame) {
                    left -= 1
                    count -= 1
                } else {
                    node.next = kept
                    kept = node
                }
                node = next
            }
            if (kept == null) chains.remove() else chain.setValue(kept)
        }
    }

    /** Without frames: drops the oldest hash's whole chain, as an LRU-by-insertion bound. */
    private fun dropOldestChain() {
        val oldest = entries.entries.iterator()
        var node: Entry? = oldest.next().value
        oldest.remove()
        while (node != null) {
            if (node.frame == frame) live -= 1
            count -= 1
            node = node.next
        }
    }

    /** Removes the span in [key]'s chain that [matches] selects, if any. */
    private inline fun unlink(key: Key, matches: (Entry) -> Boolean) {
        var previous: Entry? = null
        var node = entries[key]
        while (node != null && !matches(node)) {
            previous = node
            node = node.next
        }
        if (node == null) return
        if (previous == null) {
            val rest = node.next
            if (rest == null) entries.remove(key) else entries[key] = rest
        } else {
            previous.next = node.next
        }
        count -= 1
        if (node.frame == frame) live -= 1
    }

    private fun retainedHash(primitivesHash: Int, pathClips: List<DrawPath>, safeInteriorRect: Rectangle?): Int =
        31 * (31 * primitivesHash + pathClips.hashCode()) + (safeInteriorRect?.hashCode() ?: 0)

    /** Cache size and configuration constants. */
    companion object {
        /** Stale spans kept for a UI that comes back, such as a window closed and reopened. */
        const val DEFAULT_CAPACITY = 256

        /** [maxCapacity] over [capacity]: a frame of up to 16,384 spans by default stays wholly retained. */
        private const val MAX_LIVE_SPANS_PER_CAPACITY = 64
    }
}

/** List equality by index, with no iterator; path clips are compared on every lookup under a clip. */
private fun List<DrawPath>.sameAs(other: List<DrawPath>): Boolean {
    var index = if (size == other.size) 0 else -1
    while (index in 0 until size) index = if (get(index) == other[index]) index + 1 else -1
    return index == size
}
