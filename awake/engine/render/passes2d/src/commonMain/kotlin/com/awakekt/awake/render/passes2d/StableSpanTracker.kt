/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

/**
 * Notices when a span that [RetainedDrawRunCache] keeps missing has stopped changing, so the
 * coalescer can stage it once more, into immutable arrays, and store it (#1408).
 *
 * ## Why this exists
 *
 * [DynamicRunArrayPool] routes a span through its reused buffers on every miss after the first
 * sight of its position, and a pooled span is never stored. Without this, a span that changed once
 * -- a name plate that moved and then stood still, a label that settled after a frame -- missed
 * forever: it was restaged, and on the web copied out of linear memory for upload, every frame,
 * though nothing in it changed again.
 *
 * ## How
 *
 * Each pooled miss records the span's retained-cache hash. A miss whose hash was also a miss last
 * frame is the same content two frames running, so the span has settled: [shouldPromote] says to
 * store it, and it hits from the next frame. A span that changes every frame never repeats a hash
 * and stays pooled, allocating nothing. A hash collision only promotes a span early, which costs
 * one staging allocation; the cache still compares the full span before it serves a hit.
 *
 * ## When promotion would thrash
 *
 * The cache is least-recently-used and bounded. A frame with more cacheable spans than it holds
 * evicts every entry before it is reached again, so a stored span never hits and storing it every
 * frame would only allocate. [shouldPromote] refuses then, and the spans stay pooled.
 *
 * Allocation free in steady state: the two hash arrays grow to the largest frame and are kept.
 */
internal class StableSpanTracker {
    private var previous = IntArray(INITIAL_SPANS)
    private var previousCount = 0
    private var current = IntArray(INITIAL_SPANS)
    private var currentCount = 0
    private var cacheableSpans = 0
    private var previousCacheableSpans = 0

    /** How many spans were promoted into the retained cache so far, for probes and tests. */
    var promotions: Int = 0
        private set

    /** Starts a frame: last frame's misses become the set this frame's misses are checked against. */
    fun beginFrame() {
        val swap = previous
        previous = current
        previousCount = currentCount
        current = swap
        currentCount = 0
        previous.sort(0, previousCount)
        previousCacheableSpans = cacheableSpans
        cacheableSpans = 0
    }

    /** Counts one cacheable span, hit or miss, toward this frame's total. */
    fun countCacheableSpan() {
        cacheableSpans += 1
    }

    /**
     * Records a miss of [hash] that was not a first sight and reports whether to store the span.
     *
     * @param hash The span's retained-cache hash, from [RetainedDrawRunCache.lastMissHash].
     * @param capacity The retained cache's capacity.
     * @return `true` when the same content missed last frame too and the cache can hold every
     * cacheable span of a frame; the caller then stages fresh, immutable arrays and stores them.
     */
    fun shouldPromote(hash: Int, capacity: Int): Boolean {
        val settled = missedLastFrame(hash)
        if (settled && previousCacheableSpans <= capacity) {
            promotions += 1
            return true
        }
        if (currentCount == current.size) current = current.copyOf(current.size * 2)
        current[currentCount++] = hash
        return false
    }

    /** Binary search of last frame's sorted misses; common stdlib has no `IntArray.binarySearch`. */
    private fun missedLastFrame(hash: Int): Boolean {
        var low = 0
        var high = previousCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = previous[mid]
            when {
                value < hash -> low = mid + 1
                value > hash -> high = mid - 1
                else -> return true
            }
        }
        return false
    }

    private companion object {
        const val INITIAL_SPANS = 64
    }
}
