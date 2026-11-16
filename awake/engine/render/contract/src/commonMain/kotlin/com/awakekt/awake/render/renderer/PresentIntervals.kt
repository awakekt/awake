/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.time.TimeSource

/** Where a renderer's present intervals were measured (#1699). */
enum class PresentTiming {
    /**
     * The time between successive present calls returning, read on the CPU. It follows the display
     * once the swapchain blocks on it, but a frame the compositor drops still reads as on time.
     */
    CpuPresent,

    /** The display's own presentation timestamps, as a frame-pacing library reports them. */
    DisplayTiming,
}

/**
 * The time between a renderer's presented frames over a rolling window: what a player sees as the
 * frame rate, as opposed to how long the frame took to make. A steady 30 fps cap reads p50 and p95
 * both near 33.3 ms; pacing that alternates 16.7 ms and 50 ms reads the same mean but a wide spread.
 *
 * A backend calls [recordPresent] after each present; [RenderStatsCounter.publish] carries the
 * spread into [RenderFrameStats]. Allocation-free per frame. Not thread-safe: the render thread owns it.
 */
class PresentIntervalTracker(
    /** Intervals the spread is taken over. */
    private val windowSize: Int = DEFAULT_WINDOW,
    /** Where the intervals come from. */
    val timing: PresentTiming = PresentTiming.CpuPresent,
) {
    init {
        require(windowSize > 1) { "windowSize must be above 1: $windowSize" }
    }

    private val origin = TimeSource.Monotonic.markNow()
    private val window = LongArray(windowSize)
    private val sorted = LongArray(windowSize)
    private var size = 0
    private var next = 0
    private var previous = NO_PRESENT
    private var sortedIsCurrent = false

    /** Intervals in the window. */
    val count: Int get() = size

    /** Records a present that returned now. */
    fun recordPresent() {
        recordPresentAt(origin.elapsedNow().inWholeNanoseconds)
    }

    /** Records a present at [nanos] on any monotonic clock, the same one for every call. */
    fun recordPresentAt(nanos: Long) {
        val last = previous
        previous = nanos
        if (last != NO_PRESENT && nanos >= last) recordInterval(nanos - last)
    }

    /** Records an interval measured elsewhere, as a display-timing report gives it. */
    fun recordInterval(nanos: Long) {
        window[next] = nanos
        next = (next + 1) % windowSize
        if (size < windowSize) size += 1
        sortedIsCurrent = false
    }

    /** Forgets every interval and the last present, as after a swapchain is rebuilt or the app resumes. */
    fun reset() {
        size = 0
        next = 0
        previous = NO_PRESENT
    }

    /** The interval at [percentile] (0-100) over the window, nearest rank, or null before two presents. */
    fun percentileNanos(percentile: Int): Long? {
        if (size == 0) return null
        if (!sortedIsCurrent) {
            window.copyInto(sorted, endIndex = size)
            sorted.sort(fromIndex = 0, toIndex = size)
            sortedIsCurrent = true
        }
        val index = (percentile.coerceIn(0, MAX_PERCENTILE) * (size - 1)) / MAX_PERCENTILE
        return sorted[index]
    }

    private companion object {
        const val DEFAULT_WINDOW = 120
        const val MAX_PERCENTILE = 100
        const val NO_PRESENT = Long.MIN_VALUE
    }
}
