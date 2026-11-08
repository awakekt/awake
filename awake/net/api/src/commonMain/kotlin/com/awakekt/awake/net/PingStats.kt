/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/**
 * Fixed-bucket round-trip time (RTT) histogram and RFC 3550 interarrival jitter estimator for a
 * network session, allocating zero objects per recorded sample.
 *
 * Samples in `0..<fineLimitMillis` are binned at [fineMillis] resolution (`5 ms` by default), and
 * samples in `fineLimitMillis..maxMillis` are binned at [coarseMillis] resolution (`250 ms` by
 * default), with [percentile] returning the upper edge of the matching bucket. [jitterMs] tracks
 * consecutive RTT variation via RFC 3550's `1/16` exponentially weighted moving average.
 *
 * Thread-safe across all multiplatform targets: a connection reader may call [add] while a
 * diagnostics or session-close reporter reads [percentile], [jitterMs], [count], or [lastMs].
 *
 * @property maxMillis Maximum accepted RTT sample in milliseconds (`>= fineLimitMillis`).
 * @property fineMillis Bucket width in milliseconds below [fineLimitMillis] (must divide [fineLimitMillis]).
 * @property fineLimitMillis Threshold in milliseconds switching from [fineMillis] to [coarseMillis] buckets.
 * @property coarseMillis Bucket width in milliseconds from [fineLimitMillis] up to [maxMillis].
 */
@OptIn(ExperimentalAtomicApi::class)
class PingStats(
    val maxMillis: Int = DEFAULT_MAX_MILLIS,
    val fineMillis: Int = DEFAULT_FINE_MILLIS,
    val fineLimitMillis: Int = DEFAULT_FINE_LIMIT_MILLIS,
    val coarseMillis: Int = DEFAULT_COARSE_MILLIS,
) {
    init {
        require(fineMillis > 0) { "fineMillis must be positive, was $fineMillis" }
        require(fineLimitMillis >= fineMillis && fineLimitMillis % fineMillis == 0) {
            "fineLimitMillis ($fineLimitMillis) must be a positive multiple of fineMillis ($fineMillis)"
        }
        require(maxMillis >= fineLimitMillis) {
            "maxMillis ($maxMillis) must be >= fineLimitMillis ($fineLimitMillis)"
        }
        require(coarseMillis > 0) { "coarseMillis must be positive, was $coarseMillis" }
    }

    private val fineBuckets: Int = fineLimitMillis / fineMillis
    private val coarseBuckets: Int = (maxMillis - fineLimitMillis) / coarseMillis + 1
    private val buckets = IntArray(fineBuckets + coarseBuckets)
    private val lock = AtomicInt(0)

    private var jitter: Double = 0.0
    private var previous: Int = -1

    @Volatile
    private var sampleCount: Int = 0

    @Volatile
    private var latestMs: Int = -1

    /** Total number of valid RTT samples recorded since construction or [reset]. */
    val count: Int
        get() = sampleCount

    /** Most recently recorded RTT sample in milliseconds, or `-1` before the first valid sample. */
    val lastMs: Int
        get() = latestMs

    /**
     * Records one RTT sample of [millis] milliseconds.
     *
     * @param millis Measured round-trip duration in milliseconds.
     * @return `true` if `millis in 0..maxMillis` and the sample was recorded, or `false` if out of range.
     */
    fun add(millis: Int): Boolean {
        if (millis !in 0..maxMillis) return false
        withLock {
            buckets[bucketOf(millis)]++
            if (previous >= 0) {
                jitter += (abs(millis - previous) - jitter) / JITTER_GAIN
            }
            previous = millis
            latestMs = millis
            sampleCount++
        }
        return true
    }

    /**
     * Returns the upper bucket edge in milliseconds at or below which [fraction] (`0.0..1.0`) of
     * recorded samples fall, or `-1` when [count] is `0`.
     *
     * @param fraction Quantile in `0.0..1.0` (values outside are clamped; for example `0.5` for p50, `0.95` for p95).
     * @return Bucket upper edge in milliseconds, or `-1` when empty.
     */
    fun percentile(fraction: Double): Int = withLock {
        var answer = -1
        if (sampleCount > 0) {
            val rank = ceil(fraction.coerceIn(0.0, 1.0) * sampleCount).toInt().coerceAtLeast(1)
            var seen = 0
            answer = maxMillis
            for (i in buckets.indices) {
                seen += buckets[i]
                if (seen >= rank) {
                    answer = upperEdge(i)
                    break
                }
            }
        }
        answer
    }

    /**
     * Returns the RFC 3550 smoothed interarrival jitter rounded to whole milliseconds, or `-1`
     * before at least two samples have been recorded.
     *
     * @return Smoothed interarrival jitter in milliseconds, or `-1` when `count < 2`.
     */
    fun jitterMs(): Int = withLock {
        if (sampleCount < 2) -1 else round(jitter).toInt()
    }

    /** Clears all recorded bucket counts, jitter state, [count], and [lastMs]. */
    fun reset() {
        withLock {
            buckets.fill(0)
            jitter = 0.0
            previous = -1
            latestMs = -1
            sampleCount = 0
        }
    }

    private fun bucketOf(millis: Int): Int =
        if (millis < fineLimitMillis) {
            millis / fineMillis
        } else {
            (fineBuckets + (millis - fineLimitMillis) / coarseMillis).coerceAtMost(buckets.size - 1)
        }

    private fun upperEdge(bucket: Int): Int =
        if (bucket < fineBuckets) {
            (bucket + 1) * fineMillis
        } else {
            minOf(fineLimitMillis + (bucket - fineBuckets + 1) * coarseMillis, maxMillis)
        }

    private inline fun <T> withLock(block: () -> T): T {
        while (!lock.compareAndSet(0, 1)) {
            // Short non-allocating spin over primitive array updates.
        }
        try {
            return block()
        } finally {
            lock.store(0)
        }
    }

    /** Default histogram bounds and resolutions for [PingStats]. */
    companion object {
        /** Default maximum accepted RTT sample (`10_000 ms`). */
        const val DEFAULT_MAX_MILLIS: Int = 10_000

        /** Default fine bucket width (`5 ms`) below [DEFAULT_FINE_LIMIT_MILLIS]. */
        const val DEFAULT_FINE_MILLIS: Int = 5

        /** Default cutoff (`1_000 ms`) between fine and coarse buckets. */
        const val DEFAULT_FINE_LIMIT_MILLIS: Int = 1_000

        /** Default coarse bucket width (`250 ms`) at and above [DEFAULT_FINE_LIMIT_MILLIS]. */
        const val DEFAULT_COARSE_MILLIS: Int = 250

        private const val JITTER_GAIN = 16.0
    }
}
