/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Frame times over a run, for percentiles a phone can be compared by: [warmupMs] of frames ignored
 * while caches and the JIT settle, then frames recorded until [measuredMs] have passed and at least
 * [minFrames] are in. By time rather than by count, so a fast desktop and a slow phone each warm up
 * and measure for as long.
 *
 * Each frame records the frame's own time and the CPU time of its phases (game, render and UI), so a
 * run under vsync, where every frame takes the display's interval, still shows what the frame cost.
 */
internal class FrameTimeBenchmark(
    val warmupMs: Float = DEFAULT_WARMUP_MS,
    val measuredMs: Float = DEFAULT_MEASURED_MS,
    val minFrames: Int = DEFAULT_MIN_FRAMES,
) {
    private var frames = FloatArray(INITIAL_CAPACITY)
    private var ui = FloatArray(INITIAL_CAPACITY)
    private var cpu = FloatArray(INITIAL_CAPACITY)
    private var warmed = 0f
    private var measured = 0f

    /** Frames recorded past the warm-up. */
    var recorded: Int = 0
        private set

    /** How far through the run it is, from 0 to 1. */
    val progress: Float get() = if (warmed < warmupMs) 0f else (measured / measuredMs).coerceAtMost(1f)

    /**
     * Whether the run is over: [measuredMs] recorded, in at least [minFrames] frames, or [MAX_MEASURED_MS]
     * recorded however few frames that was, so a page the browser throttles still finishes.
     */
    val done: Boolean get() = measured >= measuredMs && (recorded >= minFrames || measured >= MAX_MEASURED_MS)

    /**
     * Records one frame: [frameMs] from its start to the next's, [uiMs] building and staging its UI,
     * [cpuMs] in all its phases. Frames of the warm-up, and any once [done], are left out.
     */
    fun record(frameMs: Float, uiMs: Float, cpuMs: Float) {
        if (done) return
        if (warmed < warmupMs) {
            warmed += frameMs
            return
        }
        if (recorded == frames.size) grow()
        frames[recorded] = frameMs
        ui[recorded] = uiMs
        cpu[recorded] = cpuMs
        recorded++
        measured += frameMs
    }

    /** The run's percentiles so far; the whole run's once [done]. */
    fun summary(): FrameTimeSummary {
        val count = recorded
        val frame = frames.copyOf(count).also { it.sort() }
        val uiSorted = ui.copyOf(count).also { it.sort() }
        val cpuSorted = cpu.copyOf(count).also { it.sort() }
        return FrameTimeSummary(
            frames = count,
            averageMs = if (count == 0) 0f else frame.sum() / count,
            p50Ms = percentile(frame, P50),
            p90Ms = percentile(frame, P90),
            p95Ms = percentile(frame, P95),
            p99Ms = percentile(frame, P99),
            maxMs = frame.lastOrNull() ?: 0f,
            uiP50Ms = percentile(uiSorted, P50),
            uiP95Ms = percentile(uiSorted, P95),
            cpuP50Ms = percentile(cpuSorted, P50),
            cpuP95Ms = percentile(cpuSorted, P95),
        )
    }

    private fun grow() {
        frames = frames.copyOf(frames.size * 2)
        ui = ui.copyOf(ui.size * 2)
        cpu = cpu.copyOf(cpu.size * 2)
    }

    companion object {
        const val DEFAULT_WARMUP_MS = 3_000f
        const val DEFAULT_MEASURED_MS = 10_000f
        const val DEFAULT_MIN_FRAMES = 300
        const val MAX_MEASURED_MS = 60_000f
        private const val INITIAL_CAPACITY = 1_024
        private const val P50 = 50f
        private const val P90 = 90f
        private const val P95 = 95f
        private const val P99 = 99f
    }
}

/** A benchmark run's frame times, in milliseconds. */
internal data class FrameTimeSummary(
    val frames: Int,
    val averageMs: Float,
    val p50Ms: Float,
    val p90Ms: Float,
    val p95Ms: Float,
    val p99Ms: Float,
    val maxMs: Float,
    val uiP50Ms: Float,
    val uiP95Ms: Float,
    val cpuP50Ms: Float,
    val cpuP95Ms: Float,
) {
    /** Stable `key=value` pairs, so a log line greps and diffs. */
    fun pairs(): String = buildString {
        append("frames=").append(frames)
        append(" avg=").append(averageMs.oneDecimal())
        append(" p50=").append(p50Ms.oneDecimal())
        append(" p90=").append(p90Ms.oneDecimal())
        append(" p95=").append(p95Ms.oneDecimal())
        append(" p99=").append(p99Ms.oneDecimal())
        append(" max=").append(maxMs.oneDecimal())
        append(" ui50=").append(uiP50Ms.oneDecimal())
        append(" ui95=").append(uiP95Ms.oneDecimal())
        append(" cpu50=").append(cpuP50Ms.oneDecimal())
        append(" cpu95=").append(cpuP95Ms.oneDecimal())
    }
}

/** The nearest-rank [percent] percentile of [sorted], ascending; 0 for none. */
internal fun percentile(sorted: FloatArray, percent: Float): Float {
    if (sorted.isEmpty()) return 0f
    val rank = ceil(percent / 100f * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** Rounded to a tenth, for logs and readouts. */
internal fun Float.oneDecimal(): Float = (this * 10f).roundToInt() / 10f
