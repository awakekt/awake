/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Evaluates a Catmull-Rom segment from [p1] to [p2] (with neighbors [p0] and [p3]) at local
 * parameter [t] in `0..1`, writing the result into [out] without allocating.
 *
 * Uses Barry-Goldman / centripetal parameterization controlled by [alpha]:
 * - `0.0f` is uniform Catmull-Rom.
 * - `0.5f` (the default) is centripetal Catmull-Rom, which eliminates loops and cusps on unevenly
 *   spaced camera waypoints.
 * - `1.0f` is chordal Catmull-Rom.
 *
 * @param p0 Control point preceding [p1].
 * @param p1 Segment start point (returned at `t = 0`).
 * @param p2 Segment end point (returned at `t = 1`).
 * @param p3 Control point following [p2].
 * @param t Normalized segment parameter clamped to `0..1`.
 * @param out Destination vector mutated in place.
 * @param alpha Parameterization exponent in `0..1` (default `0.5f`, centripetal).
 * @return [out], for chaining.
 */
@Suppress("LongParameterList")
fun catmullRomCentripetal(
    p0: Vec3f,
    p1: Vec3f,
    p2: Vec3f,
    p3: Vec3f,
    t: Float,
    out: Vec3f,
    alpha: Float = CENTRIPETAL_ALPHA,
): Vec3f {
    val clamped = t.coerceIn(0f, 1f)
    val t0 = 0f
    val t1 = t0 + knotStep(p0, p1, alpha)
    val t2 = t1 + knotStep(p1, p2, alpha)
    val t3 = t2 + knotStep(p2, p3, alpha)

    val u = lerp(t1, t2, clamped)

    // Level 1 linear blends (scalar expanded to allocate nothing).
    val a1x = blendAxis(p0.x, p1.x, t0, t1, u)
    val a1y = blendAxis(p0.y, p1.y, t0, t1, u)
    val a1z = blendAxis(p0.z, p1.z, t0, t1, u)

    val a2x = blendAxis(p1.x, p2.x, t1, t2, u)
    val a2y = blendAxis(p1.y, p2.y, t1, t2, u)
    val a2z = blendAxis(p1.z, p2.z, t1, t2, u)

    val a3x = blendAxis(p2.x, p3.x, t2, t3, u)
    val a3y = blendAxis(p2.y, p3.y, t2, t3, u)
    val a3z = blendAxis(p2.z, p3.z, t2, t3, u)

    // Level 2 blends.
    val b1x = blendAxis(a1x, a2x, t0, t2, u)
    val b1y = blendAxis(a1y, a2y, t0, t2, u)
    val b1z = blendAxis(a1z, a2z, t0, t2, u)

    val b2x = blendAxis(a2x, a3x, t1, t3, u)
    val b2y = blendAxis(a2y, a3y, t1, t3, u)
    val b2z = blendAxis(a2z, a3z, t1, t3, u)

    // Level 3 final curve point.
    return out.set(
        blendAxis(b1x, b2x, t1, t2, u),
        blendAxis(b1y, b2y, t1, t2, u),
        blendAxis(b1z, b2z, t1, t2, u),
    )
}

/**
 * A 3D centripetal Catmull-Rom spline with a precomputed arc-length lookup table for constant-speed
 * traversal along camera rails and actor paths.
 *
 * All sampling methods write into caller-provided `out` vectors and allocate nothing, so they are
 * safe to call every frame inside `System.update`.
 *
 * @param points Waypoints defining the spline (at least 2 points).
 * @property closed Whether the spline connects the last point smoothly back to the first.
 * @property alpha Parameterization exponent (`0.5f` = centripetal, `0f` = uniform, `1f` = chordal).
 * @property subdivisionsPerSegment Lookup table samples per segment used for arc-length inversion.
 */
class ArcLengthSpline3f(
    points: List<Vec3f>,
    val closed: Boolean = false,
    val alpha: Float = CENTRIPETAL_ALPHA,
    val subdivisionsPerSegment: Int = DEFAULT_SUBDIVISIONS,
) {
    /** Waypoints defining the spline, copied defensively so external mutations cannot corrupt the LUT. */
    val controlPoints: List<Vec3f>

    /** Number of curve segments along this spline. */
    val segmentCount: Int

    /** Total approximated arc length of the spline in world units. */
    val totalLength: Float

    private val cumulativeLengths: FloatArray
    private val scratchPrev = Vec3f()
    private val scratchNext = Vec3f()
    private val scratchA = Vec3f()
    private val scratchB = Vec3f()

    init {
        require(points.size >= 2) { "ArcLengthSpline3f requires at least 2 points, got ${points.size}." }
        require(subdivisionsPerSegment >= 1) { "subdivisionsPerSegment must be at least 1, got $subdivisionsPerSegment." }
        require(alpha in 0f..1f) { "alpha must be in 0..1, got $alpha." }

        controlPoints = points.map { Vec3f(it.x, it.y, it.z) }
        segmentCount = if (closed) controlPoints.size else controlPoints.size - 1
        val totalSamples = segmentCount * subdivisionsPerSegment + 1
        cumulativeLengths = FloatArray(totalSamples)

        val prev = Vec3f()
        val curr = Vec3f()
        sampleSegment(0, 0f, prev)
        var accumulated = 0f
        var sampleIndex = 1
        for (segment in 0 until segmentCount) {
            for (step in 1..subdivisionsPerSegment) {
                val localT = step.toFloat() / subdivisionsPerSegment
                sampleSegment(segment, localT, curr)
                accumulated += prev.distanceTo(curr)
                cumulativeLengths[sampleIndex++] = accumulated
                prev.set(curr)
            }
        }
        totalLength = accumulated
    }

    /**
     * Samples the world-space position at normalized arc-length fraction [normalizedDistance] in `0..1`
     * into [out]. When [closed] is true, values outside `0..1` wrap cleanly around the loop.
     *
     * @param normalizedDistance Progress along the spline (`0` = start, `1` = end).
     * @param out Destination vector mutated in place.
     * @return [out], for chaining.
     */
    fun samplePosition(normalizedDistance: Float, out: Vec3f): Vec3f {
        val fraction = normalizeFraction(normalizedDistance)
        return samplePositionAtDistance(fraction * totalLength, out)
    }

    /**
     * Samples the world-space position at world-unit [distance] along the spline into [out].
     *
     * @param distance Distance from the start of the spline in world units.
     * @param out Destination vector mutated in place.
     * @return [out], for chaining.
     */
    fun samplePositionAtDistance(distance: Float, out: Vec3f): Vec3f {
        if (totalLength <= EPSILON) {
            return out.set(controlPoints[0])
        }
        val d = if (closed) {
            distance.mod(totalLength)
        } else {
            distance.coerceIn(0f, totalLength)
        }
        val samplePos = findLutParameter(d)
        val segFloat = samplePos / subdivisionsPerSegment
        val segment = segFloat.toInt().coerceIn(0, segmentCount - 1)
        val localT = (segFloat - segment).coerceIn(0f, 1f)
        return sampleSegment(segment, localT, out)
    }

    /**
     * Computes the unit forward tangent direction at normalized arc-length fraction [normalizedDistance]
     * into [out], using central finite differences without allocating.
     *
     * @param normalizedDistance Progress along the spline (`0` = start, `1` = end).
     * @param out Destination vector mutated in place to a unit tangent (or `(0, 0, -1)` if degenerate).
     * @return [out], for chaining.
     */
    fun sampleTangent(normalizedDistance: Float, out: Vec3f): Vec3f {
        if (totalLength <= EPSILON) {
            return out.set(0f, 0f, -1f)
        }
        val f = normalizeFraction(normalizedDistance)
        val before = if (closed) normalizeFraction(f - TANGENT_STEP) else (f - TANGENT_STEP).coerceAtLeast(0f)
        val after = if (closed) normalizeFraction(f + TANGENT_STEP) else (f + TANGENT_STEP).coerceAtMost(1f)
        samplePosition(before, scratchA)
        samplePosition(after, scratchB)
        out.set(scratchB).sub(scratchA)
        return if (out.length3() <= EPSILON) out.set(0f, 0f, -1f) else out.normalize()
    }

    /**
     * Evaluates raw segment [segment] at local parameter [localT] in `0..1` into [out].
     *
     * @param segment Segment index in `0 until segmentCount`.
     * @param localT Local parameter in `0..1`.
     * @param out Destination vector mutated in place.
     * @return [out], for chaining.
     */
    fun sampleSegment(segment: Int, localT: Float, out: Vec3f): Vec3f {
        val count = controlPoints.size
        val seg = segment.coerceIn(0, segmentCount - 1)
        val p1 = controlPoints[seg]
        val p2 = controlPoints[(seg + 1) % count]
        val p0 = when {
            seg > 0 -> controlPoints[seg - 1]
            closed -> controlPoints[count - 1]
            else -> scratchPrev.set(2f * p1.x - p2.x, 2f * p1.y - p2.y, 2f * p1.z - p2.z)
        }
        val p3 = when {
            seg + 2 < count -> controlPoints[seg + 2]
            closed -> controlPoints[(seg + 2) % count]
            else -> scratchNext.set(2f * p2.x - p1.x, 2f * p2.y - p1.y, 2f * p2.z - p1.z)
        }
        return catmullRomCentripetal(p0, p1, p2, p3, localT, out, alpha)
    }

    private fun normalizeFraction(value: Float): Float =
        if (closed) value.mod(1f) else value.coerceIn(0f, 1f)

    /** Binary-searches [cumulativeLengths] and returns the continuous LUT index matching [distance]. */
    private fun findLutParameter(distance: Float): Float {
        var low = 0
        var high = cumulativeLengths.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (cumulativeLengths[mid] <= distance) {
                low = mid
            } else {
                high = mid
            }
        }
        val d0 = cumulativeLengths[low]
        val d1 = cumulativeLengths[high]
        val span = d1 - d0
        val frac = if (span > EPSILON) ((distance - d0) / span).coerceIn(0f, 1f) else 0f
        return low + frac
    }

    /** Constants for [ArcLengthSpline3f]. */
    companion object {
        /** Default lookup subdivisions per spline segment. */
        const val DEFAULT_SUBDIVISIONS: Int = 16
    }
}

private fun knotStep(a: Vec3f, b: Vec3f, alpha: Float): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val dz = b.z - a.z
    val distSq = dx * dx + dy * dy + dz * dz
    if (distSq <= EPSILON_SQ) return MIN_KNOT_STEP
    val step = if (alpha == CENTRIPETAL_ALPHA) {
        sqrt(sqrt(distSq))
    } else if (alpha == 0f) {
        1f
    } else {
        sqrt(distSq).pow(alpha)
    }
    return step.coerceAtLeast(MIN_KNOT_STEP)
}

private fun blendAxis(v0: Float, v1: Float, t0: Float, t1: Float, u: Float): Float {
    val denom = t1 - t0
    if (denom <= EPSILON) return v0
    val w1 = (u - t0) / denom
    return v0 + (v1 - v0) * w1
}

private const val CENTRIPETAL_ALPHA = 0.5f
private const val EPSILON = 1e-6f
private const val EPSILON_SQ = 1e-12f
private const val MIN_KNOT_STEP = 1e-4f
private const val TANGENT_STEP = 1e-3f
