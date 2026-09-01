/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.graphics2d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pure geometry questions about points, contours and polygons -- no meshes, no tessellation.
 *
 * Used by both fill tessellation and mesh clipping, which is why they live apart from either.
 */

fun DrawPath.containsPoint(x: Float, y: Float): Boolean {
    val contours = flattenContours()
    if (contours.isEmpty()) return false
    return when (fillRule) {
        FillRule.EvenOdd -> contours.count { contour -> contour.closed && contour.containsPoint(x, y) } % 2 == 1
        FillRule.NonZero -> contours.sumOf { contour -> if (contour.closed) contour.windingContribution(x, y) else 0 } != 0
    }
}

fun DrawPath.containsPoint(point: DrawPoint): Boolean = containsPoint(point.x, point.y)

fun PathContour.containsPoint(x: Float, y: Float): Boolean = windingContribution(x, y) != 0

private val NORMAL_SIDES = floatArrayOf(-1f, 1f)

fun PathContour.containsContour(inner: PathContour): Boolean {
    val nudge = 0.01f
    var tried = 0
    for (index in inner.points.indices) {
        val a = inner.points[index]
        val b = inner.points[(index + 1) % inner.points.size]
        var dx = b.x - a.x
        var dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length <= 0f) continue
        dx /= length
        dy /= length
        val midX = (a.x + b.x) / 2f
        val midY = (a.y + b.y) / 2f
        for (side in NORMAL_SIDES) {
            val px = midX + dy * nudge * side
            val py = midY - dx * nudge * side
            if (inner.windingContribution(px, py) == 0) continue
            return windingContribution(px, py) != 0
        }
        tried += 1
        if (tried >= 32) break
    }
    return false
}

fun PathContour.windingContribution(x: Float, y: Float): Int {
    val polygon = points
    if (polygon.size < 3) return 0

    var windingNumber = 0
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        if (a.y <= y) {
            if (b.y > y && isLeft(a, b, x, y) > 0f) windingNumber += 1
        } else if (b.y <= y && isLeft(a, b, x, y) < 0f) {
            windingNumber -= 1
        }
    }
    return windingNumber
}

fun isLeft(a: DrawPoint, b: DrawPoint, x: Float, y: Float): Float =
    (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)

/**
 * A polygon is convex when it turns the same way at every vertex AND turns exactly once in total.
 *
 * Same-sign turns alone is not enough: a spiral turns one way the whole way round and still is not
 * convex. Stroke outlines produce exactly that -- a stroked arc's ring runs the outer side, sweeps
 * a cap, runs the inner side back and caps again, so it accumulates roughly twice the turning of
 * the arc itself. Heroicons' `user-circle` and `light-bulb` cleared the sign test and were fanned
 * from their centroid, which filled the hole the outline was supposed to leave.
 */
fun isConvex(points: List<DrawPoint>): Boolean {
    if (points.size < 3) return false
    // Edge directions, with zero-length edges dropped. A contour that draws back to its start
    // before closing carries one, and pairing raw vertex triples across it silently discards the
    // turn at that vertex -- enough to push a genuinely convex rounded rectangle out of tolerance.
    val dirs = ArrayList<DrawPoint>(points.size)
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        dirs += unitDir(a, b) ?: continue
    }
    if (dirs.size < 3) return false

    var sign = 0
    var turning = 0f
    for (i in dirs.indices) {
        val u = dirs[i]
        val v = dirs[(i + 1) % dirs.size]
        val cross = u.x * v.y - u.y * v.x
        turning += atan2(cross, u.x * v.x + u.y * v.y)
        if (cross == 0f) continue
        val currentSign = if (cross > 0f) 1 else -1
        if (sign == 0) {
            sign = currentSign
        } else if (sign != currentSign) {
            return false
        }
    }
    return sign != 0 && abs(abs(turning) - TWO_PI) < TURNING_EPSILON
}

private const val TWO_PI = 2f * PI.toFloat()

/** Slack for float error accumulated over a finely flattened contour. The nearest non-convex
 * turning total is 4*PI away, so this can be loose. */
private const val TURNING_EPSILON = 0.1f

fun polygonSignedArea(points: List<DrawPoint>): Float {
    var area = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area / 2f
}
