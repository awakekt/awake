/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.core

/**
 * Frame rate and frame-time spread over a rolling window.
 *
 * An average hides the frames a player feels: a run of 8 ms frames with one 60 ms stall still
 * averages near 120 fps. [p99FrameTimeMs] and [maxFrameTimeMs] are what show the stall.
 * Allocation-free per frame, so measuring does not add the garbage it is trying to catch.
 */
class FrameStats(
    private val sampleWindowSeconds: Float = 1f,
    /** Frames the spread is taken over, independent of [sampleWindowSeconds]'s fps cadence. */
    private val percentileWindowSize: Int = 120,
) {
    init {
        require(percentileWindowSize > 0) { "percentileWindowSize must be positive." }
    }

    /** Frames per second over the last completed [sampleWindowSeconds]. */
    var fps: Float = 0f
        private set

    /** The most recent frame's time. */
    var frameTimeMs: Float = 0f
        private set

    private var accumulator = 0f
    private var frameCount = 0

    private val window = FloatArray(percentileWindowSize)
    private val sorted = FloatArray(percentileWindowSize)
    private var size = 0
    private var next = 0
    private var sortedIsCurrent = false

    /** Records one frame; returns true when [fps] was just republished. */
    fun update(deltaSeconds: Float): Boolean {
        frameTimeMs = deltaSeconds * MS_PER_SECOND
        accumulator += deltaSeconds
        frameCount += 1

        window[next] = frameTimeMs
        next = (next + 1) % percentileWindowSize
        if (size < percentileWindowSize) size += 1
        sortedIsCurrent = false

        if (accumulator < sampleWindowSeconds) {
            return false
        }

        fps = frameCount / accumulator
        accumulator = 0f
        frameCount = 0
        return true
    }

    /** Mean frame time over the window, or 0 before any frame. */
    val averageFrameTimeMs: Float
        get() {
            if (size == 0) return 0f
            var sum = 0f
            for (i in 0 until size) sum += window[i]
            return sum / size
        }

    /** Slowest frame in the window, or 0 before any frame. */
    val maxFrameTimeMs: Float
        get() = percentileFrameTimeMs(MAX_PERCENTILE)

    /**
     * Frame time, in ms, at [percentile] (0-100) over the window, nearest-rank on the sorted
     * frames; `percentileFrameTimeMs(95f)` is the p95 frame time. Returns 0 before any frame.
     */
    fun percentileFrameTimeMs(percentile: Float): Float {
        if (size == 0) return 0f
        if (!sortedIsCurrent) {
            window.copyInto(sorted, endIndex = size)
            sorted.sort(fromIndex = 0, toIndex = size)
            sortedIsCurrent = true
        }
        val index = ((percentile / MAX_PERCENTILE) * (size - 1)).toInt().coerceIn(0, size - 1)
        return sorted[index]
    }

    val p50FrameTimeMs: Float get() = percentileFrameTimeMs(50f)
    val p95FrameTimeMs: Float get() = percentileFrameTimeMs(95f)
    val p99FrameTimeMs: Float get() = percentileFrameTimeMs(99f)

    private companion object {
        const val MS_PER_SECOND = 1000f
        const val MAX_PERCENTILE = 100f
    }
}
