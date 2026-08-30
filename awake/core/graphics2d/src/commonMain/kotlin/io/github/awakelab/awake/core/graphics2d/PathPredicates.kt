/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.graphics2d

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

fun isConvex(points: List<DrawPoint>): Boolean {
    if (points.size < 3) return false
    var sign = 0
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        val c = points[(i + 2) % points.size]
        val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        if (cross == 0f) continue
        val currentSign = if (cross > 0f) 1 else -1
        if (sign == 0) {
            sign = currentSign
        } else if (sign != currentSign) {
            return false
        }
    }
    return sign != 0
}

fun polygonSignedArea(points: List<DrawPoint>): Float {
    var area = 0f
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area / 2f
}
