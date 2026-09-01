/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.graphics2d

import io.github.awakelab.awake.core.color.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import io.github.awakelab.awake.core.math2d.dp

fun DrawPath.tessellateStroke(stroke: DrawStroke, density: Float = 1f): TriangleMesh {
    if (stroke.width.value <= 0f || density <= 0f) return TriangleMesh(emptyList(), IntArray(0))
    // Construct one continuous outline before triangulation. Segment-by-segment quads lose the
    // source cap and join semantics, which turns small Lucide checks into disconnected strips.
    val scaledStroke = stroke.copy(width = (stroke.width.value * density).dp)
    return strokeToFillPath(scaledStroke).tessellateFill()
}

/** Tessellates a stroke with an anti-aliased fringe that fits within thin strokes. */
fun DrawPath.tessellateStrokeAa(
    stroke: DrawStroke,
    color: Color,
    density: Float = 1f,
    fringePx: Float = AA_FRINGE_PX,
): ColoredTriangleMesh {
    if (stroke.width.value <= 0f || density <= 0f) return ColoredTriangleMesh(emptyList(), IntArray(0))
    val physicalWidth = stroke.width.value * density
    val scaledStroke = stroke.copy(width = physicalWidth.dp)
    val fringe = fringePx.coerceAtLeast(0f).coerceAtMost(physicalWidth / 2f)
    // A stroke already has a finite interior. Keep that core opaque and place the fringe outside
    // it so a one-pixel border does not lose all of its solid coverage.
    return strokeToFillPath(scaledStroke).tessellateFillAa(color, fringePx = fringe, insetPx = 0f)
}

/**
 * Converts a stroked centerline into an equivalent FILLED outline, so a stroke can be rendered
 * through [tessellateFill]/[tessellateFillAa].
 */
fun DrawPath.strokeToFillPath(stroke: DrawStroke): DrawPath {
    val contours = flattenContours(curveSteps = 16, arcStepDegrees = STROKE_ARC_STEP_DEGREES)
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
        // A contour that ends where it began is a loop, whether or not it said `Z`. It has to be
        // stroked as one: the open path emits a single ring that walks the outer boundary and then
        // the inner boundary the *same way round*, so under NonZero the windings add instead of
        // cancelling and the hole fills. Heroicons' outline tier is full of these.
        //
        // The caps a truly open path would get at that point are dropped, which is the correct
        // picture here: a cap drawn on top of the join it coincides with is invisible.
        val loops = contour.points.size > 2 && unitDir(contour.points.last(), contour.points.first()) == null
        if (contour.closed || loops) {
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
    val steps = adaptiveArcSteps(delta * 180f / piF, radius, STROKE_ARC_STEP_DEGREES)
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
    val steps = adaptiveArcSteps(180f, halfWidth, STROKE_ARC_STEP_DEGREES)
    return (0..steps).map { step ->
        val angle = fromAngle - piF * (step / steps.toFloat())
        DrawPoint(center.x + cos(angle) * halfWidth, center.y + sin(angle) * halfWidth)
    }
}

private const val STROKE_ARC_STEP_DEGREES = 6f

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
