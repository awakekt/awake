/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("MatchingDeclarationName")

package com.awakekt.awake.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Standard timing easing curves mapping normalized progress `t` in `0..1` to an eased fraction.
 *
 * Pure, deterministic, and allocation-free, suitable for camera transitions, dolly rails, speed
 * ramps, and lighting/atmosphere blends.
 */
enum class EasingCurve {
    /** Constant speed with no acceleration or deceleration. */
    Linear,

    /** Hermite cubic smoothstep (`3t^2 - 2t^3`). */
    SmoothStep,

    /** Ken Perlin quintic smootherstep (`6t^5 - 15t^4 + 10t^3`) with zero acceleration at endpoints. */
    SmootherStep,

    /** Quadratic ease-in accelerating from rest. */
    EaseInQuad,

    /** Quadratic ease-out decelerating smoothly to rest. */
    EaseOutQuad,

    /** Symmetric quadratic ease-in and ease-out. */
    EaseInOutQuad,

    /** Cubic ease-in accelerating from rest. */
    EaseInCubic,

    /** Cubic ease-out decelerating smoothly to rest. */
    EaseOutCubic,

    /** Symmetric cubic ease-in and ease-out. */
    EaseInOutCubic,

    /** Sinusoidal ease-in and ease-out for organic camera sweeps. */
    EaseInOutSine,

    /** Exponential ease-in for sharp whip builds. */
    EaseInExpo,

    /** Exponential ease-out for rapid camera snaps settling smoothly. */
    EaseOutExpo,

    /** Overshooting ease-out useful for whip-pan settle punch. */
    EaseOutBack,
    ;

    /**
     * Evaluates this easing curve at progress [t], clamped to `0..1`.
     *
     * @param t Normalized progress from `0f` to `1f`.
     * @return Eased interpolation weight (usually in `0..1`, slightly exceeding `1` for [EaseOutBack]).
     */
    @Suppress("CyclomaticComplexMethod")
    fun evaluate(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (this) {
            Linear -> x
            SmoothStep -> x * x * (THREE - TWO * x)
            SmootherStep -> x * x * x * (x * (x * SIX - FIFTEEN) + TEN)
            EaseInQuad -> x * x
            EaseOutQuad -> 1f - (1f - x) * (1f - x)
            EaseInOutQuad -> if (x < HALF) TWO * x * x else 1f - (-TWO * x + TWO).let { it * it } * HALF
            EaseInCubic -> x * x * x
            EaseOutCubic -> 1f - (1f - x).let { it * it * it }
            EaseInOutCubic -> if (x < HALF) FOUR * x * x * x else 1f - (-TWO * x + TWO).let { it * it * it } * HALF
            EaseInOutSine -> -(cos(PI_F * x) - 1f) * HALF
            EaseInExpo -> if (x <= 0f) 0f else TWO.pow(TEN * x - TEN)
            EaseOutExpo -> if (x >= 1f) 1f else 1f - TWO.pow(-TEN * x)
            EaseOutBack -> {
                val u = x - 1f
                1f + BACK_C3 * u * u * u + BACK_C1 * u * u
            }
        }
    }
}

/**
 * Evaluates a CSS/animation-style unit cubic Bézier easing curve with endpoints `(0, 0)` and `(1, 1)`
 * and interior control handles `([x1], [y1])` and `([x2], [y2])` at horizontal progress [t].
 *
 * Uses Newton-Raphson refinement with a bisection fallback and allocates nothing.
 *
 * @param t Normalized time progress in `0..1`.
 * @param x1 X coordinate of the first handle, clamped to `0..1`.
 * @param y1 Y coordinate of the first handle.
 * @param x2 X coordinate of the second handle, clamped to `0..1`.
 * @param y2 Y coordinate of the second handle.
 * @return Eased vertical value at [t].
 */
fun cubicBezierEasing(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val targetX = t.coerceIn(0f, 1f)
    if (targetX <= 0f || targetX >= 1f) return targetX
    val cx1 = x1.coerceIn(0f, 1f)
    val cx2 = x2.coerceIn(0f, 1f)

    // Solve Bx(u) = targetX for u in 0..1.
    var low = 0f
    var high = 1f
    var u = targetX
    for (iteration in 0 until BEZIER_MAX_ITERATIONS) {
        val x = sampleBezier1D(u, cx1, cx2)
        val err = x - targetX
        if (abs(err) <= BEZIER_EPSILON) break
        if (err < 0f) low = u else high = u
        val slope = sampleBezierDerivative1D(u, cx1, cx2)
        val next = if (abs(slope) > BEZIER_SLOPE_MIN) u - err / slope else (low + high) * HALF
        u = if (next > low && next < high) next else (low + high) * HALF
    }
    return sampleBezier1D(u, y1, y2)
}

private fun sampleBezier1D(u: Float, p1: Float, p2: Float): Float {
    val inv = 1f - u
    return THREE * inv * inv * u * p1 + THREE * inv * u * u * p2 + u * u * u
}

private fun sampleBezierDerivative1D(u: Float, p1: Float, p2: Float): Float {
    val inv = 1f - u
    return THREE * inv * inv * p1 + SIX * inv * u * (p2 - p1) + THREE * u * u * (1f - p2)
}

private const val HALF = 0.5f
private const val TWO = 2f
private const val THREE = 3f
private const val FOUR = 4f
private const val SIX = 6f
private const val TEN = 10f
private const val FIFTEEN = 15f
private const val PI_F = PI.toFloat()
private const val BACK_C1 = 1.70158f
private const val BACK_C3 = BACK_C1 + 1f
private const val BEZIER_MAX_ITERATIONS = 10
private const val BEZIER_EPSILON = 1e-5f
private const val BEZIER_SLOPE_MIN = 1e-5f
