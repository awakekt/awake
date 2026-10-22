/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One glyph's signed distance field, from its outline moved into atlas texels (y down).
 *
 * Inside is non-zero winding. The distance is to the nearest point of the filled shape's edge, not
 * of every contour: variable-font instances such as the bundled Roboto draw 'H' as two stems and a
 * bar that overlap, and an edge buried inside another contour would otherwise draw a seam. For a
 * texel outside, the nearest outline point is always on the edge. For one inside, a candidate
 * point counts only if one side of it is unfilled.
 *
 * Where two contours cross, the crossing itself is never a candidate, so a texel inside both and
 * within a texel of it reads as fully inside. That inner corner draws a little sharper than a true
 * distance field would draw it, but never with a seam.
 *
 * @param outline the glyph's outline in font units, y up.
 * @param penX the pen origin's atlas x, in texels.
 * @param baseline the baseline's atlas y, in texels.
 * @param scale atlas texels per font unit.
 */
internal class GlyphField(outline: List<OutlineSegment>, penX: Double, baseline: Double, scale: Double) {
    private val curves = outline.map { segment ->
        val p = segment.points
        FieldCurve(DoubleArray(8) { if (it % 2 == 0) penX + p[it] * scale else baseline - p[it] * scale }, segment.isLine)
    }
    private val candidateDistances = DoubleArray(curves.size)
    private val candidatePoints = DoubleArray(curves.size * 2)
    private val nearest = DoubleArray(2)

    /** The atlas value at ([px], [py]): 0.5 on the outline, 1 at [REACH] inside, 0 at [REACH] outside. */
    fun value(px: Double, py: Double): Double {
        val inside = winding(px, py) != 0
        val distance = edgeDistance(px, py, inside)
        return (if (inside) distance else -distance) / (2 * REACH) + 0.5
    }

    private fun winding(px: Double, py: Double): Int = curves.sumOf { it.crossings(px, py) }

    /** The distance to the nearest edge point, capped at [REACH]. */
    private fun edgeDistance(px: Double, py: Double, inside: Boolean): Double {
        var count = 0
        for (curve in curves) {
            if (curve.isNear(px, py, REACH)) {
                candidateDistances[count] = curve.nearestSquared(px, py, nearest)
                candidatePoints[count * 2] = nearest[0]
                candidatePoints[count * 2 + 1] = nearest[1]
                count++
            }
        }
        var result = REACH
        var remaining = count
        while (remaining > 0) {
            var best = 0
            for (index in 1 until count) if (candidateDistances[index] < candidateDistances[best]) best = index
            val distance = sqrt(candidateDistances[best])
            candidateDistances[best] = Double.POSITIVE_INFINITY
            remaining = if (distance < REACH) remaining - 1 else 0
            val onEdge = distance < REACH && (!inside || isEdge(px, py, candidatePoints[best * 2], candidatePoints[best * 2 + 1], distance))
            if (onEdge) {
                result = distance
                remaining = 0
            }
        }
        return result
    }

    /**
     * Whether the outline point ([qx], [qy]), [distance] from ([px], [py]), has fill on one side only.
     * A texel centre right on the outline has no direction to step in, so it looks for an unfilled side.
     */
    private fun isEdge(px: Double, py: Double, qx: Double, qy: Double, distance: Double): Boolean {
        if (distance == 0.0) {
            return winding(px + SIDE_STEP, py) == 0 || winding(px - SIDE_STEP, py) == 0 ||
                winding(px, py + SIDE_STEP) == 0 || winding(px, py - SIDE_STEP) == 0
        }
        val ux = (qx - px) / distance * SIDE_STEP
        val uy = (qy - py) / distance * SIDE_STEP
        return (winding(qx + ux, qy + uy) == 0) != (winding(qx - ux, qy - uy) == 0)
    }

    private companion object {
        /** The farthest distance the atlas holds, in texels: half the generator's two-texel range. */
        const val REACH = 1.0
        const val SIDE_STEP = 1e-4
    }
}

/** A line or cubic in atlas texels, with what the field asks of it: crossings for winding, and the nearest point. */
internal class FieldCurve(internal val p: DoubleArray, internal val isLine: Boolean) {
    private val minX = min(min(p[0], p[2]), min(p[4], p[6]))
    private val maxX = max(max(p[0], p[2]), max(p[4], p[6]))
    private val minY = min(min(p[1], p[3]), min(p[5], p[7]))
    private val maxY = max(max(p[1], p[3]), max(p[5], p[7]))

    /** Parameters splitting the curve where y turns, so y only rises or only falls between two of them. */
    private val breaks: DoubleArray = if (isLine) {
        doubleArrayOf(0.0, 1.0)
    } else {
        (doubleArrayOf(0.0) + turningPoints(p[1], p[3], p[5], p[7]).sortedArray() + 1.0)
    }
    private val breakYs = DoubleArray(breaks.size) { at(breaks[it], 1) }

    fun isNear(px: Double, py: Double, reach: Double): Boolean =
        px >= minX - reach && px <= maxX + reach && py >= minY - reach && py <= maxY + reach

    /** Signed crossings of the ray from ([px], [py]) towards +x. Each span counts from its lower y up to, not including, its upper y. */
    fun crossings(px: Double, py: Double): Int {
        if (py < minY || py >= maxY || px >= maxX) return 0
        var winding = 0
        for (piece in 0 until breaks.size - 1) {
            val y0 = breakYs[piece]
            val y1 = breakYs[piece + 1]
            val spans = py >= min(y0, y1) && py < max(y0, y1)
            if (spans && (minX > px || xAtY(breaks[piece], breaks[piece + 1], y1 > y0, py) > px)) {
                winding += if (y1 > y0) 1 else -1
            }
        }
        return winding
    }

    /** Where a span on which y only rises or only falls reaches [py]: exact for a line, bisected for a cubic. */
    private fun xAtY(from: Double, to: Double, rising: Boolean, py: Double): Double {
        if (isLine) return p[0] + (py - p[1]) * (p[6] - p[0]) / (p[7] - p[1])
        var low = from
        var high = to
        repeat(BISECTION_STEPS) {
            val middle = (low + high) / 2
            if ((at(middle, 1) < py) == rising) low = middle else high = middle
        }
        return at((low + high) / 2, 0)
    }

    /** The squared distance to the nearest point, which goes into [nearest]. Cubics search it by Newton's method from several starts. */
    fun nearestSquared(px: Double, py: Double, nearest: DoubleArray): Double {
        val t = if (isLine) {
            val dx = p[6] - p[0]
            val dy = p[7] - p[1]
            (((px - p[0]) * dx + (py - p[1]) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        } else {
            nearestOnCubic(px, py)
        }
        nearest[0] = at(t, 0)
        nearest[1] = at(t, 1)
        return squaredDistance(t, px, py)
    }

    private fun nearestOnCubic(px: Double, py: Double): Double {
        var bestT = if (squaredDistance(0.0, px, py) <= squaredDistance(1.0, px, py)) 0.0 else 1.0
        var best = squaredDistance(bestT, px, py)
        for (start in 0..SEARCH_STARTS) {
            var t = start.toDouble() / SEARCH_STARTS
            var step = 0
            while (step < SEARCH_STEPS && t >= 0.0 && t <= 1.0) {
                t = newtonStep(t, px, py)
                val distance = if (t >= 0.0 && t <= 1.0) squaredDistance(t, px, py) else Double.POSITIVE_INFINITY
                if (distance < best) {
                    best = distance
                    bestT = t
                }
                step++
            }
        }
        return bestT
    }

    private fun squaredDistance(t: Double, px: Double, py: Double): Double {
        val dx = at(t, 0) - px
        val dy = at(t, 1) - py
        return dx * dx + dy * dy
    }

    /** One Newton step towards where the curve's tangent is square to the line from the point; NaN when it stalls. */
    private fun newtonStep(t: Double, px: Double, py: Double): Double {
        val qx = at(t, 0) - px
        val qy = at(t, 1) - py
        val d1x = derivative(t, 0)
        val d1y = derivative(t, 1)
        val d2x = secondDerivative(t, 0)
        val d2y = secondDerivative(t, 1)
        val denominator = d1x * d1x + d1y * d1y + qx * d2x + qy * d2y
        return if (denominator == 0.0) Double.NaN else t - (qx * d1x + qy * d1y) / denominator
    }

    /** The curve's x ([axis] 0) or y ([axis] 1) at [t]. */
    private fun at(t: Double, axis: Int): Double =
        if (isLine) p[axis] + t * (p[6 + axis] - p[axis]) else cubic(t, p[axis], p[2 + axis], p[4 + axis], p[6 + axis])

    private fun derivative(t: Double, axis: Int): Double {
        val u = 1 - t
        return 3 * (u * u * (p[2 + axis] - p[axis]) + 2 * u * t * (p[4 + axis] - p[2 + axis]) + t * t * (p[6 + axis] - p[4 + axis]))
    }

    private fun secondDerivative(t: Double, axis: Int): Double =
        6 * ((1 - t) * (p[4 + axis] - 2 * p[2 + axis] + p[axis]) + t * (p[6 + axis] - 2 * p[4 + axis] + p[2 + axis]))

    private companion object {
        const val BISECTION_STEPS = 40
        const val SEARCH_STARTS = 4
        const val SEARCH_STEPS = 6
    }
}

/** The box an outline fills, its curves' extremes included, as minX, minY, maxX, maxY; null when it has no ink. */
internal fun outlineBounds(outline: List<OutlineSegment>): DoubleArray? {
    if (outline.isEmpty()) return null
    val box = doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
    fun add(x: Double, y: Double) {
        box[0] = min(box[0], x)
        box[1] = min(box[1], y)
        box[2] = max(box[2], x)
        box[3] = max(box[3], y)
    }
    for (segment in outline) {
        val p = segment.points
        add(p[0], p[1])
        add(p[6], p[7])
        // A turning point extends one axis; pairing it with the start's other coordinate, already in the box, leaves that axis as it is.
        if (!segment.isLine) {
            for (t in turningPoints(p[0], p[2], p[4], p[6])) add(cubic(t, p[0], p[2], p[4], p[6]), p[1])
            for (t in turningPoints(p[1], p[3], p[5], p[7])) add(p[0], cubic(t, p[1], p[3], p[5], p[7]))
        }
    }
    return box.takeIf { it[2] > it[0] && it[3] > it[1] }
}

/**
 * Where a cubic coordinate through [a0]..[a3] turns round strictly inside the curve: up to two parameters.
 *
 * The derivative's roots by the cancellation-free form of the quadratic formula. A quadratic raised to a
 * cubic leaves a leading coefficient that is zero but for rounding; that form still finds its root.
 */
private fun turningPoints(a0: Double, a1: Double, a2: Double, a3: Double): DoubleArray {
    val a = -a0 + 3 * a1 - 3 * a2 + a3
    val b = 2 * (a0 - 2 * a1 + a2)
    val c = a1 - a0
    val discriminant = b * b - 4 * a * c
    if (discriminant < 0) return DoubleArray(0)
    val root = sqrt(discriminant)
    val q = -0.5 * (b + if (b >= 0) root else -root)
    return doubleArrayOf(q / a, c / q).filter { it > 0.0 && it < 1.0 }.distinct().toDoubleArray()
}

private fun cubic(t: Double, a0: Double, a1: Double, a2: Double, a3: Double): Double {
    val u = 1 - t
    return u * u * u * a0 + 3 * u * u * t * a1 + 3 * u * t * t * a2 + t * t * t * a3
}
