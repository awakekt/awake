// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun DrawPath.tessellateStroke(stroke: DrawStroke, density: Float = 1f): TriangleMesh {
    val contours = flattenContours()
    if (contours.isEmpty()) return TriangleMesh(emptyList(), IntArray(0))

    val halfWidth = (stroke.width.value * density) / 2f
    if (halfWidth <= 0f) return TriangleMesh(emptyList(), IntArray(0))

    val points = ArrayList<DrawPoint>()
    val indices = ArrayList<Int>()
    contours.forEach { contour ->
        val vertices = contour.points
        if (vertices.size < 2) return@forEach

        val segmentCount = if (contour.closed) vertices.size else vertices.size - 1
        for (i in 0 until segmentCount) {
            val start = vertices[i]
            val end = vertices[(i + 1) % vertices.size]
            var dx = end.x - start.x
            var dy = end.y - start.y
            val length = hypot(dx, dy)
            if (length <= 0f) continue

            dx /= length
            dy /= length
            val extend = if (!contour.closed && stroke.cap == StrokeCap.Square) halfWidth else 0f
            val startX = if (!contour.closed && i == 0) start.x - dx * extend else start.x
            val startY = if (!contour.closed && i == 0) start.y - dy * extend else start.y
            val endX = if (!contour.closed && i == segmentCount - 1) end.x + dx * extend else end.x
            val endY = if (!contour.closed && i == segmentCount - 1) end.y + dy * extend else end.y

            val nx = -dy * halfWidth
            val ny = dx * halfWidth
            val base = points.size
            points += DrawPoint(startX + nx, startY + ny)
            points += DrawPoint(endX + nx, endY + ny)
            points += DrawPoint(endX - nx, endY - ny)
            points += DrawPoint(startX - nx, startY - ny)

            indices += base
            indices += base + 1
            indices += base + 2
            indices += base + 2
            indices += base + 3
            indices += base
        }
    }
    return TriangleMesh(points, indices.toIntArray())
}

/**
 * Converts a stroked centerline into an equivalent FILLED outline, so a stroke can be rendered
 * through [tessellateFill]/[tessellateFillAa].
 */
fun DrawPath.strokeToFillPath(stroke: DrawStroke): DrawPath {
    val contours = flattenContours(curveSteps = 16, arcStepDegrees = 15f)
    val halfWidth = stroke.width.value / 2f
    if (contours.isEmpty() || halfWidth <= 0f) return DrawPath(fillRule = FillRule.NonZero, commands = emptyList())

    val commands = ArrayList<PathCommand>()
    fun emitRing(ring: List<DrawPoint>) {
        if (ring.size < 3) return
        commands += PathCommand.MoveTo(ring[0].x, ring[0].y)
        for (i in 1 until ring.size) commands += PathCommand.LineTo(ring[i].x, ring[i].y)
        commands += PathCommand.Close
    }

    contours.forEach { contour ->
        if (contour.closed) {
            emitRing(offsetClosedRing(contour.points, halfWidth, stroke.join))
            emitRing(offsetClosedRing(contour.points, -halfWidth, stroke.join).asReversed())
        } else {
            emitRing(offsetOpenRing(contour.points, halfWidth, stroke))
        }
    }
    return DrawPath(fillRule = FillRule.NonZero, commands = commands)
}

internal fun unitDir(a: DrawPoint, b: DrawPoint): DrawPoint? {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx, dy)
    return if (length <= 0f) null else DrawPoint(dx / length, dy / length)
}

internal fun offsetVector(dir: DrawPoint, distance: Float): DrawPoint = DrawPoint(-dir.y * distance, dir.x * distance)

internal fun arcBetween(center: DrawPoint, radius: Float, from: DrawPoint, to: DrawPoint): List<DrawPoint> {
    val piF = PI.toFloat()
    val fromAngle = atan2(from.y, from.x)
    val toAngle = atan2(to.y, to.x)
    val twoPi = 2f * piF
    var delta = toAngle - fromAngle
    while (delta > piF) delta -= twoPi
    while (delta < -piF) delta += twoPi
    val steps = adaptiveArcSteps(delta * 180f / piF, radius, 15f)
    return (0..steps).map { step ->
        val t = step / steps.toFloat()
        val angle = fromAngle + delta * t
        DrawPoint(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
    }
}

internal fun capSweep(round: Boolean, center: DrawPoint, halfWidth: Float, dir: DrawPoint): List<DrawPoint> {
    if (!round) {
        val left = offsetVector(dir, halfWidth)
        val right = offsetVector(DrawPoint(-dir.x, -dir.y), halfWidth)
        return listOf(DrawPoint(center.x + left.x, center.y + left.y), DrawPoint(center.x + right.x, center.y + right.y))
    }
    val piF = PI.toFloat()
    val fromAngle = atan2(dir.y, dir.x) + piF / 2f
    val steps = adaptiveArcSteps(180f, halfWidth, 15f)
    return (0..steps).map { step ->
        val angle = fromAngle - piF * (step / steps.toFloat())
        DrawPoint(center.x + cos(angle) * halfWidth, center.y + sin(angle) * halfWidth)
    }
}

internal fun ringCorner(round: Boolean, center: DrawPoint, halfWidth: Float, fromDir: DrawPoint, toDir: DrawPoint): List<DrawPoint> {
    val from = offsetVector(fromDir, halfWidth)
    val to = offsetVector(toDir, halfWidth)
    return if (round) {
        arcBetween(center, halfWidth, from, to)
    } else {
        listOf(DrawPoint(center.x + from.x, center.y + from.y), DrawPoint(center.x + to.x, center.y + to.y))
    }
}

internal fun offsetOpenRing(points: List<DrawPoint>, halfWidth: Float, stroke: DrawStroke): List<DrawPoint> {
    if (points.size < 2) return emptyList()
    val dirs = ArrayList<DrawPoint>(points.size - 1)
    for (i in 0 until points.size - 1) dirs += unitDir(points[i], points[i + 1]) ?: return emptyList()

    var pts = points
    if (stroke.cap == StrokeCap.Square) {
        val extendedFirst = DrawPoint(pts.first().x - dirs.first().x * halfWidth, pts.first().y - dirs.first().y * halfWidth)
        val extendedLast = DrawPoint(pts.last().x + dirs.last().x * halfWidth, pts.last().y + dirs.last().y * halfWidth)
        pts = listOf(extendedFirst) + pts.subList(1, pts.size - 1) + listOf(extendedLast)
    }
    val round = stroke.join == StrokeJoin.Round
    val roundCap = stroke.cap == StrokeCap.Round

    val ring = ArrayList<DrawPoint>()
    ring += DrawPoint(pts.first().x + offsetVector(dirs.first(), halfWidth).x, pts.first().y + offsetVector(dirs.first(), halfWidth).y)
    for (i in 1 until dirs.size) ring += ringCorner(round, pts[i], halfWidth, dirs[i - 1], dirs[i])
    ring += capSweep(roundCap, pts.last(), halfWidth, dirs.last())
    for (i in dirs.size - 2 downTo 0) {
        ring += ringCorner(round, pts[i + 1], halfWidth, DrawPoint(-dirs[i + 1].x, -dirs[i + 1].y), DrawPoint(-dirs[i].x, -dirs[i].y))
    }
    ring += capSweep(roundCap, pts.first(), halfWidth, DrawPoint(-dirs.first().x, -dirs.first().y))
    return ring
}

internal fun offsetClosedRing(rawPoints: List<DrawPoint>, distance: Float, join: StrokeJoin): List<DrawPoint> {
    val points = if (rawPoints.size >= 2 && unitDir(rawPoints.last(), rawPoints.first()) == null) {
        rawPoints.dropLast(1)
    } else {
        rawPoints
    }
    val n = points.size
    if (n < 3) return emptyList()
    val dirs = (0 until n).map { i -> unitDir(points[i], points[(i + 1) % n]) ?: return emptyList() }
    val round = join == StrokeJoin.Round
    val ring = ArrayList<DrawPoint>()
    for (i in 0 until n) {
        val fromDir = dirs[(i - 1 + n) % n]
        val toDir = dirs[i]
        ring += ringCorner(round, points[i], abs(distance), scaleDir(fromDir, distance), scaleDir(toDir, distance))
    }
    return ring
}

private fun scaleDir(dir: DrawPoint, distance: Float): DrawPoint = if (distance >= 0f) dir else DrawPoint(-dir.x, -dir.y)
