/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Below this vertical speed (units per second), a bouncing particle settles instead of bouncing
 * again so a restitution near `1` comes visibly to rest rather than micro-hopping forever.
 */
const val DEFAULT_BOUNCE_STOP_VELOCITY: Float = 0.3f

internal const val DEGREES_TO_RADIANS: Float = (kotlin.math.PI / 180.0).toFloat()
internal const val FULL_TURN_RADIANS: Float = (2.0 * kotlin.math.PI).toFloat()

/**
 * Keeps `activeSeconds / burstInterval` landing on an exact integer (`1.0 / 0.25`) from rounding
 * up to one extra burst when floating-point division lands slightly above the whole number.
 */
private const val BURST_BOUNDARY_TOLERANCE: Float = 1e-4f

/**
 * Opacity envelope over a particle's normalized lifetime (`0` at birth, `1` at death): fades in
 * linearly from `0` to `1` over `0..fadeInEnd`, holds `1` through [fadeOutStart], then fades out
 * linearly to `0` at `1`.
 *
 * `ParticleAlphaRamp(0f, 0f)` reproduces the default linear fade from birth to death. Both
 * fractions must lie in `0..1` with `fadeInEnd <= fadeOutStart`.
 *
 * @property fadeInEnd Normalized age (`0..1`) where fade-in reaches full opacity.
 * @property fadeOutStart Normalized age (`0..1`) where fade-out toward zero begins.
 */
data class ParticleAlphaRamp(
    val fadeInEnd: Float = 0f,
    val fadeOutStart: Float = 0f,
) {
    init {
        require(fadeInEnd in 0f..1f && fadeOutStart in 0f..1f) {
            "alpha ramp fractions must be in 0..1, got fadeInEnd=$fadeInEnd fadeOutStart=$fadeOutStart"
        }
        require(fadeInEnd <= fadeOutStart) {
            "fadeInEnd ($fadeInEnd) must not come after fadeOutStart ($fadeOutStart)"
        }
    }

    /** Opacity multiplier in `0..1` at [lifeFraction] (`0` at spawn, `1` at death). */
    fun factorAt(lifeFraction: Float): Float =
        alphaRampFactorAt(lifeFraction, fadeInEnd, fadeOutStart)
}

internal fun alphaRampFactorAt(lifeFraction: Float, fadeInEnd: Float, fadeOutStart: Float): Float {
    val t = lifeFraction.coerceIn(0f, 1f)
    return when {
        t < fadeInEnd -> t / fadeInEnd
        t < fadeOutStart -> 1f
        t >= 1f -> 0f
        else -> (1f - t) / (1f - fadeOutStart)
    }
}

/**
 * Looping pulsed emission schedule: every [cycleSeconds], during the first [activeSeconds], emits
 * [burstSize] particles every [burstInterval] seconds, then pauses until the next cycle starts.
 *
 * @property cycleSeconds Duration of one full cycle in seconds; must be positive and finite.
 * @property activeSeconds Active window from the start of each cycle; must lie in `(0, cycleSeconds]`.
 * @property burstInterval Seconds between bursts inside the active window; must be positive and finite.
 * @property burstSize Number of particles requested per burst; must be positive.
 */
data class ParticleBurstSchedule(
    val cycleSeconds: Float,
    val activeSeconds: Float,
    val burstInterval: Float,
    val burstSize: Int,
) {
    init {
        require(cycleSeconds > 0f && cycleSeconds.isFinite()) {
            "cycleSeconds must be positive and finite, got $cycleSeconds"
        }
        require(activeSeconds > 0f && activeSeconds <= cycleSeconds) {
            "activeSeconds must be in (0, cycleSeconds], got $activeSeconds of $cycleSeconds"
        }
        require(burstInterval > 0f && burstInterval.isFinite()) {
            "burstInterval must be positive and finite, got $burstInterval"
        }
        require(burstSize > 0) { "burstSize must be positive, got $burstSize" }
    }

    /** Total number of bursts that have started at or before [time] seconds into the emitter's life. */
    fun burstsStartedBy(time: Float): Int =
        scheduleBurstsStartedBy(time, cycleSeconds, activeSeconds, burstInterval)
}

internal fun scheduleBurstsStartedBy(
    time: Float,
    cycleSeconds: Float,
    activeSeconds: Float,
    burstInterval: Float,
): Int {
    if (time <= 0f) return 0
    val burstsPerCycle = ceil(activeSeconds / burstInterval - BURST_BOUNDARY_TOLERANCE).toInt().coerceAtLeast(1)
    val completeCycles = floor(time / cycleSeconds).toInt()
    val phase = time - completeCycles * cycleSeconds
    val inThisCycle = (floor(phase / burstInterval).toInt() + 1).coerceAtMost(burstsPerCycle)
    return completeCycles * burstsPerCycle + inThisCycle
}

/**
 * Per-particle angular velocity range drawn uniformly at spawn between [minDegreesPerSecond] and
 * [maxDegreesPerSecond], optionally starting from a uniform random angle in `[0, 2π)` when
 * [randomStartAngle] is `true`.
 *
 * @property minDegreesPerSecond Minimum angular velocity in degrees per second (finite, `<= maxDegreesPerSecond`).
 * @property maxDegreesPerSecond Maximum angular velocity in degrees per second (finite, `>= minDegreesPerSecond`).
 * @property randomStartAngle Whether each spawned particle starts at a random orientation instead of `0`.
 */
data class ParticleSpinRange(
    val minDegreesPerSecond: Float,
    val maxDegreesPerSecond: Float,
    val randomStartAngle: Boolean = false,
) {
    init {
        require(minDegreesPerSecond.isFinite() && maxDegreesPerSecond.isFinite()) {
            "spin rates must be finite, got min=$minDegreesPerSecond max=$maxDegreesPerSecond"
        }
        require(minDegreesPerSecond <= maxDegreesPerSecond) {
            "spin min must not be above max, got min=$minDegreesPerSecond max=$maxDegreesPerSecond"
        }
    }
}
