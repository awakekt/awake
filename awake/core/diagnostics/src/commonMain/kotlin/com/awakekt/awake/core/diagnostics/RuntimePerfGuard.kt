/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.math.ceil

/**
 * Threshold, hysteresis, and backoff parameters for [RuntimePerfGuard].
 *
 * Defaults target sustained 30 FPS (`33.3 ms` budget) on constrained mobile hardware; use
 * [forTargetFps] to derive matched thresholds for another frame-rate target (such as 60 FPS).
 *
 * @property windowFrames Number of recent frame durations held in the rolling ring buffer (`>= 1`).
 * @property evaluationIntervalFrames Number of frames between quantile evaluations once the window
 *   is full (`1..windowFrames`).
 * @property quantile Nearest-rank quantile in `(0.0, 1.0]` evaluated over the rolling window
 *   (default `0.90`, i.e. p90 frame time).
 * @property slowThresholdMs Rolling quantile (ms) at or above which [RuntimePerfGuard.Tier.Normal]
 *   counts toward stepping down to [RuntimePerfGuard.Tier.Slow].
 * @property severeThresholdMs Rolling quantile (ms) at or above which [RuntimePerfGuard] counts
 *   toward stepping down to [RuntimePerfGuard.Tier.Severe] (`>= slowThresholdMs`).
 * @property recoverToNormalMs Rolling quantile (ms) at or below which [RuntimePerfGuard.Tier.Slow]
 *   counts toward recovering to [RuntimePerfGuard.Tier.Normal] (`< slowThresholdMs`).
 * @property recoverToSlowMs Rolling quantile (ms) at or below which [RuntimePerfGuard.Tier.Severe]
 *   counts toward recovering to [RuntimePerfGuard.Tier.Slow] (`< severeThresholdMs`).
 * @property degradeAfterWindows Consecutive slow evaluation windows required before stepping down.
 * @property recoverAfterWindows Initial consecutive calm evaluation windows required before
 *   stepping up; doubles on repeated degrade-after-recovery oscillations up to
 *   [maxRecoverAfterWindows].
 * @property maxRecoverAfterWindows Upper cap for backed-off recovery windows
 *   (`>= recoverAfterWindows`).
 * @property recoveryBackoffMultiplier Factor applied to required recovery windows when a session
 *   degrades again after a previous recovery (`>= 1`).
 * @property cooldownFrames Minimum frames after a tier transition before another transition may
 *   occur (`>= 0`).
 * @property maxSampleMs Upper clamp applied to individual frame durations so a one-off OS pause
 *   or background resume hitch counts as one slow frame without dominating the window; pass
 *   [Double.POSITIVE_INFINITY] to disable clamping.
 */
@Suppress("LongParameterList")
data class RuntimePerfGuardConfig(
    val windowFrames: Int = DEFAULT_WINDOW_FRAMES,
    val evaluationIntervalFrames: Int = DEFAULT_EVALUATION_INTERVAL_FRAMES,
    val quantile: Double = DEFAULT_QUANTILE,
    val slowThresholdMs: Double = DEFAULT_SLOW_THRESHOLD_MS,
    val severeThresholdMs: Double = DEFAULT_SEVERE_THRESHOLD_MS,
    val recoverToNormalMs: Double = DEFAULT_RECOVER_TO_NORMAL_MS,
    val recoverToSlowMs: Double = DEFAULT_RECOVER_TO_SLOW_MS,
    val degradeAfterWindows: Int = DEFAULT_DEGRADE_AFTER_WINDOWS,
    val recoverAfterWindows: Int = DEFAULT_RECOVER_AFTER_WINDOWS,
    val maxRecoverAfterWindows: Int = DEFAULT_MAX_RECOVER_AFTER_WINDOWS,
    val recoveryBackoffMultiplier: Int = DEFAULT_RECOVERY_BACKOFF_MULTIPLIER,
    val cooldownFrames: Int = DEFAULT_COOLDOWN_FRAMES,
    val maxSampleMs: Double = DEFAULT_MAX_SAMPLE_MS,
) {
    init {
        require(windowFrames >= 1) { "windowFrames must be >= 1, was $windowFrames." }
        require(evaluationIntervalFrames in 1..windowFrames) {
            "evaluationIntervalFrames must be in 1..$windowFrames, was $evaluationIntervalFrames."
        }
        require(quantile.isFinite() && quantile > 0.0 && quantile <= 1.0) {
            "quantile must be in (0.0, 1.0], was $quantile."
        }
        require(slowThresholdMs.isFinite() && slowThresholdMs > 0.0) {
            "slowThresholdMs must be finite and > 0.0, was $slowThresholdMs."
        }
        require(severeThresholdMs.isFinite() && severeThresholdMs >= slowThresholdMs) {
            "severeThresholdMs must be >= slowThresholdMs ($slowThresholdMs), was $severeThresholdMs."
        }
        require(recoverToNormalMs.isFinite() && recoverToNormalMs > 0.0 && recoverToNormalMs < slowThresholdMs) {
            "recoverToNormalMs must be in (0.0, slowThresholdMs ($slowThresholdMs)), was $recoverToNormalMs."
        }
        require(recoverToSlowMs.isFinite() && recoverToSlowMs >= recoverToNormalMs && recoverToSlowMs < severeThresholdMs) {
            "recoverToSlowMs must be in [$recoverToNormalMs, $severeThresholdMs), was $recoverToSlowMs."
        }
        require(degradeAfterWindows >= 1) { "degradeAfterWindows must be >= 1, was $degradeAfterWindows." }
        require(recoverAfterWindows >= 1) { "recoverAfterWindows must be >= 1, was $recoverAfterWindows." }
        require(maxRecoverAfterWindows >= recoverAfterWindows) {
            "maxRecoverAfterWindows must be >= recoverAfterWindows ($recoverAfterWindows), was $maxRecoverAfterWindows."
        }
        require(recoveryBackoffMultiplier >= 1) {
            "recoveryBackoffMultiplier must be >= 1, was $recoveryBackoffMultiplier."
        }
        require(cooldownFrames >= 0) { "cooldownFrames must be >= 0, was $cooldownFrames." }
        require(!maxSampleMs.isNaN() && maxSampleMs > 0.0) { "maxSampleMs must be > 0.0, was $maxSampleMs." }
    }

    /** Default constants and target-FPS presets for [RuntimePerfGuardConfig]. */
    companion object {
        /** Default rolling ring size (60 frames, ~2 seconds at 30 FPS). */
        const val DEFAULT_WINDOW_FRAMES: Int = 60

        /** Default cadence between quantile checks once the ring is full (30 frames). */
        const val DEFAULT_EVALUATION_INTERVAL_FRAMES: Int = 30

        /** Default rolling quantile (p90 frame time). */
        const val DEFAULT_QUANTILE: Double = 0.90

        /** Default slow threshold (`38.0 ms`, ~26.3 FPS) for a 30 FPS mobile target. */
        const val DEFAULT_SLOW_THRESHOLD_MS: Double = 38.0

        /** Default severe threshold (`55.0 ms`, ~18.2 FPS) for a 30 FPS mobile target. */
        const val DEFAULT_SEVERE_THRESHOLD_MS: Double = 55.0

        /** Default recovery threshold back to Normal (`30.0 ms`, ~33.3 FPS). */
        const val DEFAULT_RECOVER_TO_NORMAL_MS: Double = 30.0

        /** Default recovery threshold from Severe back to Slow (`42.0 ms`, ~23.8 FPS). */
        const val DEFAULT_RECOVER_TO_SLOW_MS: Double = 42.0

        /** Default consecutive slow windows required before stepping down. */
        const val DEFAULT_DEGRADE_AFTER_WINDOWS: Int = 2

        /** Default consecutive calm windows required before stepping up. */
        const val DEFAULT_RECOVER_AFTER_WINDOWS: Int = 4

        /** Default ceiling for backed-off recovery windows after oscillation. */
        const val DEFAULT_MAX_RECOVER_AFTER_WINDOWS: Int = 16

        /** Default backoff multiplier applied to recovery windows after oscillation. */
        const val DEFAULT_RECOVERY_BACKOFF_MULTIPLIER: Int = 2

        /** Default cooldown frames after any transition before evaluating again. */
        const val DEFAULT_COOLDOWN_FRAMES: Int = 60

        /** Default single-frame hitch clamp (`250.0 ms`). */
        const val DEFAULT_MAX_SAMPLE_MS: Double = 250.0

        private const val MILLIS_PER_SECOND: Double = 1000.0
        private const val SLOW_BUDGET_FACTOR: Double = 1.15
        private const val SEVERE_BUDGET_FACTOR: Double = 1.65
        private const val RECOVER_NORMAL_BUDGET_FACTOR: Double = 0.90
        private const val RECOVER_SLOW_BUDGET_FACTOR: Double = 1.25

        /**
         * Builds a [RuntimePerfGuardConfig] scaled to [targetFps] (for example `30` or `60`),
         * preserving hysteresis bands between degrade and recovery thresholds.
         */
        fun forTargetFps(
            targetFps: Int,
            windowFrames: Int = DEFAULT_WINDOW_FRAMES,
        ): RuntimePerfGuardConfig {
            require(targetFps >= 1) { "targetFps must be >= 1, was $targetFps." }
            require(windowFrames >= 1) { "windowFrames must be >= 1, was $windowFrames." }
            val budgetMs = MILLIS_PER_SECOND / targetFps.toDouble()
            return RuntimePerfGuardConfig(
                windowFrames = windowFrames,
                evaluationIntervalFrames = maxOf(1, windowFrames / 2),
                slowThresholdMs = budgetMs * SLOW_BUDGET_FACTOR,
                severeThresholdMs = budgetMs * SEVERE_BUDGET_FACTOR,
                recoverToNormalMs = budgetMs * RECOVER_NORMAL_BUDGET_FACTOR,
                recoverToSlowMs = budgetMs * RECOVER_SLOW_BUDGET_FACTOR,
                cooldownFrames = windowFrames,
            )
        }
    }
}

/**
 * Allocation-free rolling-quantile frame-time guard with hysteresis, post-transition cooldown, and
 * exponential recovery backoff.
 *
 * Applications feed per-frame wall durations in milliseconds via [recordFrame] and react to
 * [Tier] changes (`Normal`, `Slow`, `Severe`) by scaling resolution, effect quality, draw distance,
 * or background work without encoding render-specific policies into diagnostics.
 *
 * Not thread-safe; intended for the single main/render frame loop.
 *
 * @property config Threshold, window, hysteresis, and backoff parameters.
 */
class RuntimePerfGuard(
    val config: RuntimePerfGuardConfig = RuntimePerfGuardConfig(),
) {
    /** Performance degradation tier reported by [RuntimePerfGuard]. */
    enum class Tier {
        /** Frame time is within budget; full authored settings apply. */
        Normal,

        /** Sustained frame time exceeded [RuntimePerfGuardConfig.slowThresholdMs]; trim optional cost. */
        Slow,

        /** Sustained frame time exceeded [RuntimePerfGuardConfig.severeThresholdMs]; apply aggressive fallback. */
        Severe,
    }

    private val samples = DoubleArray(config.windowFrames)
    private val scratch = DoubleArray(config.windowFrames)
    private var next = 0
    private var framesSinceEval = 0
    private var framesSinceTransition = config.cooldownFrames
    private var degradeStreak = 0
    private var recoverStreak = 0

    /** Current performance tier. */
    var tier: Tier = Tier.Normal
        private set

    /** Number of valid samples currently buffered in the rolling window (`0..windowFrames`). */
    var sampleCount: Int = 0
        private set

    /** Most recently evaluated rolling quantile in milliseconds, or `0.0` before the first full window. */
    var lastQuantileMs: Double = 0.0
        private set

    /** Current number of consecutive calm windows required before stepping up a tier (grows with backoff). */
    var requiredRecoverWindows: Int = config.recoverAfterWindows
        private set

    /** Total tier transitions observed since construction or [reset]. */
    var transitions: Int = 0
        private set

    /** Number of step-down transitions observed since construction or [reset]. */
    var degradeCount: Int = 0
        private set

    /** Number of step-up transitions observed since construction or [reset]. */
    var recoverCount: Int = 0
        private set

    /**
     * Records one frame duration in milliseconds and returns the resulting [Tier].
     *
     * Non-finite or non-positive durations (`<= 0.0`, `NaN`, infinite) are ignored. Allocates zero
     * heap bytes in steady state, including during quantile evaluation and tier transitions.
     */
    fun recordFrame(frameMs: Double): Tier {
        if (!frameMs.isFinite() || frameMs <= 0.0) return tier
        val clamped = if (frameMs > config.maxSampleMs) config.maxSampleMs else frameMs
        samples[next] = clamped
        next = (next + 1) % samples.size
        if (sampleCount < samples.size) sampleCount++
        framesSinceEval++
        if (framesSinceTransition < config.cooldownFrames) framesSinceTransition++

        if (sampleCount == samples.size &&
            framesSinceEval >= config.evaluationIntervalFrames &&
            framesSinceTransition >= config.cooldownFrames
        ) {
            framesSinceEval = 0
            evaluateWindow()
        }
        return tier
    }

    /**
     * Records [frameMs] and invokes [onTransition] inline whenever [tier] changes.
     */
    inline fun recordFrame(frameMs: Double, onTransition: (from: Tier, to: Tier) -> Unit): Tier {
        val before = tier
        val after = recordFrame(frameMs)
        if (after != before) onTransition(before, after)
        return after
    }

    /**
     * Clears the rolling window, resets [tier] to [Tier.Normal], and restores initial hysteresis
     * and recovery backoff (for example on scene change or manual quality override).
     */
    fun reset() {
        next = 0
        sampleCount = 0
        framesSinceEval = 0
        framesSinceTransition = config.cooldownFrames
        degradeStreak = 0
        recoverStreak = 0
        tier = Tier.Normal
        lastQuantileMs = 0.0
        requiredRecoverWindows = config.recoverAfterWindows
        transitions = 0
        degradeCount = 0
        recoverCount = 0
    }

    private fun evaluateWindow() {
        val count = samples.size
        samples.copyInto(scratch, destinationOffset = 0, startIndex = 0, endIndex = count)
        scratch.sort(0, count)
        val rankIndex = (ceil(config.quantile * count).toInt() - 1).coerceIn(0, count - 1)
        val reading = scratch[rankIndex]
        lastQuantileMs = reading
        when (tier) {
            Tier.Normal -> evaluateNormal(reading)
            Tier.Slow -> evaluateSlow(reading)
            Tier.Severe -> evaluateSevere(reading)
        }
    }

    private fun evaluateNormal(reading: Double) {
        if (reading >= config.slowThresholdMs) {
            degradeStreak++
            recoverStreak = 0
            if (degradeStreak >= config.degradeAfterWindows) {
                val target = if (reading >= config.severeThresholdMs) Tier.Severe else Tier.Slow
                transitionTo(target)
            }
        } else {
            degradeStreak = 0
        }
    }

    private fun evaluateSlow(reading: Double) {
        when {
            reading >= config.severeThresholdMs -> {
                degradeStreak++
                recoverStreak = 0
                if (degradeStreak >= config.degradeAfterWindows) transitionTo(Tier.Severe)
            }
            reading <= config.recoverToNormalMs -> {
                recoverStreak++
                degradeStreak = 0
                if (recoverStreak >= requiredRecoverWindows) transitionTo(Tier.Normal)
            }
            else -> {
                degradeStreak = 0
                recoverStreak = 0
            }
        }
    }

    private fun evaluateSevere(reading: Double) {
        if (reading <= config.recoverToSlowMs) {
            recoverStreak++
            degradeStreak = 0
            if (recoverStreak >= requiredRecoverWindows) transitionTo(Tier.Slow)
        } else {
            recoverStreak = 0
        }
    }

    private fun transitionTo(nextTier: Tier) {
        val degrading = nextTier.ordinal > tier.ordinal
        tier = nextTier
        transitions++
        if (degrading) {
            degradeCount++
            if (recoverCount > 0 && config.recoveryBackoffMultiplier > 1) {
                val multiplied = requiredRecoverWindows.toLong() * config.recoveryBackoffMultiplier.toLong()
                requiredRecoverWindows = minOf(config.maxRecoverAfterWindows.toLong(), multiplied).toInt()
            }
        } else {
            recoverCount++
        }
        // Flush pre-transition samples so the new tier is judged purely on its own frame times.
        next = 0
        sampleCount = 0
        framesSinceEval = 0
        framesSinceTransition = 0
        degradeStreak = 0
        recoverStreak = 0
    }

    /** Convenience constructors for [RuntimePerfGuard]. */
    companion object {
        /** Creates a [RuntimePerfGuard] tuned for [targetFps] (such as `30` or `60`). */
        fun forTargetFps(
            targetFps: Int,
            windowFrames: Int = RuntimePerfGuardConfig.DEFAULT_WINDOW_FRAMES,
        ): RuntimePerfGuard = RuntimePerfGuard(RuntimePerfGuardConfig.forTargetFps(targetFps, windowFrames))
    }
}
