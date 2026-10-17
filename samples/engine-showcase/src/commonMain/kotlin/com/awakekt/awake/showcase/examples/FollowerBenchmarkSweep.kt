/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples

import com.awakekt.awake.showcase.FrameTimeBenchmark

/** One benchmark run of the many-followers showcase: [followers] walkers with their plates following by [rules]. */
internal data class FollowerBenchmarkRun(val followers: Int, val rules: PlateRules)

/**
 * The many-followers benchmark: [runs] one after another, each a [FrameTimeBenchmark], each ending
 * in one `BENCH` line of frame-time percentiles and the plates laid out a frame.
 */
internal class FollowerBenchmarkSweep(runs: List<FollowerBenchmarkRun>) {
    private val queue = ArrayDeque(runs)
    private var bench: FrameTimeBenchmark? = null
    private var laidOutSum = 0L

    /** The run in progress, or null between runs. */
    var run: FollowerBenchmarkRun? = null
        private set

    /** Each finished run's line, without its `BENCH many-followers` prefix, in order. */
    val results = ArrayList<String>()

    /** Whether every run is in. */
    val finished: Boolean get() = run == null && queue.isEmpty()

    /** How far through the run in progress it is, from 0 to 1. */
    val progress: Float get() = bench?.progress ?: 0f

    /** The next run, started, when none is in progress; null while one runs or when none is left. */
    fun next(): FollowerBenchmarkRun? {
        if (run != null) return null
        return queue.removeFirstOrNull()?.also {
            run = it
            bench = FrameTimeBenchmark()
            laidOutSum = 0
        }
    }

    /**
     * Records one frame of the run in progress, with the plates [laidOut] in it; returns the run's
     * `BENCH` line when this frame finishes it, and null otherwise.
     */
    fun record(frameMs: Float, uiMs: Float, cpuMs: Float, laidOut: Int): String? {
        val bench = bench ?: return null
        val before = bench.recorded
        bench.record(frameMs, uiMs, cpuMs)
        if (bench.recorded > before) laidOutSum += laidOut
        return if (bench.done) finish(bench) else null
    }

    private fun finish(bench: FrameTimeBenchmark): String {
        val summary = bench.summary()
        val run = checkNotNull(run)
        this.bench = null
        this.run = null
        val laidOut = laidOutSum / summary.frames.coerceAtLeast(1)
        val rules = run.rules
        val line = "followers=${run.followers} occlusion=${onOff(rules.occlusion)} scale=${onOff(rules.scale)} laidOut=$laidOut ${summary.pairs()}"
        results += line
        return "$PREFIX $line"
    }

    companion object {
        /** What every line of the benchmark starts with, so a log greps for it. */
        const val PREFIX = "BENCH many-followers"
    }
}

/** `on` or `off`, as the benchmark lines and the readout say it. */
internal fun onOff(on: Boolean): String = if (on) "on" else "off"
