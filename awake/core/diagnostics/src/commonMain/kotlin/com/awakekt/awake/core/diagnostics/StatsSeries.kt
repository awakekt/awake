/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.math.ceil
import kotlin.math.round

/**
 * Fixed-capacity ring buffer of timestamped `Double` readings that summarizes any trailing time
 * window into a [StatsSummary] (`avg`, `p50`, `p95`, `max`) without per-sample allocations.
 *
 * Non-finite (`NaN` or infinite) values passed to [add] or produced by a `transform` in [summary]
 * are ignored so missing or unmeasured probes never poison a window. Not thread-safe; intended for
 * single-thread frame or telemetry loops.
 *
 * @property capacity Maximum number of readings retained in the ring (`>= 1`).
 */
class StatsSeries(val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity >= 1) {
            "capacity must be >= 1, was $capacity."
        }
    }

    private val timestamps = DoubleArray(capacity)
    private val values = DoubleArray(capacity)
    private val scratch = DoubleArray(capacity)
    private var next = 0

    /** Current number of finite readings stored in the ring (`0..capacity`). */
    var size: Int = 0
        private set

    /**
     * Appends [value] recorded at [nowSeconds], ignoring non-finite [value] readings.
     *
     * @param nowSeconds Finite timestamp of the reading in seconds.
     * @param value Metric reading to record; ignored when `NaN` or infinite.
     */
    fun add(nowSeconds: Double, value: Double) {
        require(nowSeconds.isFinite()) {
            "nowSeconds must be finite, was $nowSeconds."
        }
        if (value.isFinite()) {
            timestamps[next] = nowSeconds
            values[next] = value
            next = (next + 1) % capacity
            if (size < capacity) size++
        }
    }

    /** Clears all retained readings without reallocating backing storage. */
    fun clear() {
        next = 0
        size = 0
    }

    /**
     * Summarizes all stored readings with timestamps `>= sinceSeconds`.
     *
     * @param sinceSeconds Inclusive lower timestamp bound in seconds.
     * @param roundStep Quantization step applied to each summary field (`0.0` disables rounding).
     * @return Window summary, or `null` when no finite readings fall at or after [sinceSeconds].
     */
    fun summary(sinceSeconds: Double, roundStep: Double = DEFAULT_ROUND_STEP): StatsSummary? =
        computeSummary(sinceSeconds, roundStep, null)

    /**
     * Summarizes all stored readings with timestamps `>= sinceSeconds` after mapping each reading
     * through [transform] (for example converting frame duration in milliseconds to FPS).
     *
     * Non-finite results from [transform] are skipped.
     *
     * @param sinceSeconds Inclusive lower timestamp bound in seconds.
     * @param roundStep Quantization step applied to each summary field (`0.0` disables rounding).
     * @param transform Mapping applied to each raw reading before aggregation.
     * @return Window summary, or `null` when no finite mapped readings remain.
     */
    fun summary(
        sinceSeconds: Double,
        roundStep: Double = DEFAULT_ROUND_STEP,
        transform: (Double) -> Double,
    ): StatsSummary? = computeSummary(sinceSeconds, roundStep, transform)

    private fun computeSummary(
        sinceSeconds: Double,
        roundStep: Double,
        transform: ((Double) -> Double)?,
    ): StatsSummary? {
        require(sinceSeconds.isFinite()) {
            "sinceSeconds must be finite, was $sinceSeconds."
        }
        require(roundStep >= 0.0 && roundStep.isFinite()) {
            "roundStep must be finite and non-negative, was $roundStep."
        }
        var count = 0
        var sum = 0.0
        for (i in 0 until size) {
            if (timestamps[i] >= sinceSeconds) {
                val mapped = if (transform != null) transform(values[i]) else values[i]
                if (mapped.isFinite()) {
                    scratch[count++] = mapped
                    sum += mapped
                }
            }
        }
        if (count == 0) return null
        scratch.sort(0, count)
        return StatsSummary(
            avg = quantize(sum / count, roundStep),
            p50 = quantize(nearestRank(count, P50_QUANTILE), roundStep),
            p95 = quantize(nearestRank(count, P95_QUANTILE), roundStep),
            max = quantize(scratch[count - 1], roundStep),
        )
    }

    private fun nearestRank(count: Int, quantile: Double): Double {
        val index = (ceil(quantile * count).toInt() - 1).coerceIn(0, count - 1)
        return scratch[index]
    }

    private fun quantize(value: Double, step: Double): Double =
        if (step > 0.0) round(value / step) * step else value

    /** Default configuration constants for [StatsSeries]. */
    companion object {
        /** Default ring capacity (320 samples, covering 30 seconds at 10 Hz with headroom). */
        const val DEFAULT_CAPACITY: Int = 320

        /** Default rounding step (`0.1`, one decimal place). */
        const val DEFAULT_ROUND_STEP: Double = 0.1

        private const val P50_QUANTILE: Double = 0.50
        private const val P95_QUANTILE: Double = 0.95
    }
}
