/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class FillGroup(
    val outer: List<DrawPoint>,
    val holes: List<List<DrawPoint>>,
)

typealias UiFillGroup = FillGroup

fun resolveFillGroups(contours: List<PathContour>, fillRule: FillRule): List<FillGroup> {
    val closed = ArrayList<PathContour>()
    val simple = ArrayList<FillGroup>()
    contours.forEach { contour ->
        if (contour.closed && contour.points.size >= 3) {
            closed += contour
        } else if (contour.points.size >= 3) {
            simple += FillGroup(contour.points, emptyList())
        }
    }
    if (closed.size <= 1) {
        closed.forEach { simple += FillGroup(it.points, emptyList()) }
        return simple
    }

    val areas = closed.map { polygonSignedArea(it.points) }
    val containers = closed.indices.map { i ->
        closed.indices.filter { j -> j != i && closed[j].containsContour(closed[i]) }
    }
    val isHole = BooleanArray(closed.size)
    for (i in closed.indices) {
        val depth = containers[i].size
        if (depth == 0) continue
        val parent = containers[i].minByOrNull { abs(areas[it]) } ?: continue
        isHole[i] = when (fillRule) {
            FillRule.EvenOdd -> depth % 2 == 1
            FillRule.NonZero -> areas[i] * areas[parent] < 0f
        }
    }

    val groups = ArrayList<FillGroup>(simple)
    for (i in closed.indices) {
        if (isHole[i]) continue
        val holes = closed.indices
            .filter { h ->
                isHole[h] && i == containers[h].filter { !isHole[it] }.minByOrNull { abs(areas[it]) }
            }
            .map { h ->
                if (areas[h] * areas[i] > 0f) closed[h].points.asReversed() else closed[h].points
            }
        groups += FillGroup(closed[i].points, holes)
    }
    return groups
}

fun scanlineFillTriangulation(
    contours: List<List<DrawPoint>>,
    fillRule: FillRule,
): Pair<List<DrawPoint>, IntArray> {
    class Edge(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val dir: Int)

    val edges = ArrayList<Edge>()
    contours.forEach { polygon ->
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            if (a.y == b.y) continue
            edges += if (a.y < b.y) Edge(a.x, a.y, b.x, b.y, 1) else Edge(b.x, b.y, a.x, a.y, -1)
        }
    }
    if (edges.isEmpty()) return emptyList<DrawPoint>() to IntArray(0)

    fun xAt(edge: Edge, y: Float): Float =
        edge.x0 + (edge.x1 - edge.x0) * (y - edge.y0) / (edge.y1 - edge.y0)

    val ys = edges.flatMap { listOf(it.y0, it.y1) }.distinct().sorted()
    val points = ArrayList<DrawPoint>()
    val indices = ArrayList<Int>()
    for (s in 0 until ys.size - 1) {
        val yTop = ys[s]
        val yBottom = ys[s + 1]
        if (yBottom <= yTop) continue
        val midY = (yTop + yBottom) / 2f
        val active = edges.filter { it.y0 <= yTop && it.y1 >= yBottom }.sortedBy { xAt(it, midY) }
        var winding = 0
        var crossings = 0
        var openEdge: Edge? = null
        active.forEach { edge ->
            val wasInside = when (fillRule) {
                FillRule.NonZero -> winding != 0
                FillRule.EvenOdd -> crossings % 2 == 1
            }
            winding += edge.dir
            crossings += 1
            val isInside = when (fillRule) {
                FillRule.NonZero -> winding != 0
                FillRule.EvenOdd -> crossings % 2 == 1
            }
            if (!wasInside && isInside) {
                openEdge = edge
            } else if (wasInside && !isInside) {
                val left = openEdge ?: return@forEach
                openEdge = null
                val leftTopX = xAt(left, yTop)
                val rightTopX = xAt(edge, yTop)
                val leftBottomX = xAt(left, yBottom)
                val rightBottomX = xAt(edge, yBottom)
                if (rightTopX <= leftTopX && rightBottomX <= leftBottomX) return@forEach
                val base = points.size
                points += DrawPoint(leftTopX, yTop)
                points += DrawPoint(rightTopX, yTop)
                points += DrawPoint(rightBottomX, yBottom)
                points += DrawPoint(leftBottomX, yBottom)
                indices += base
                indices += base + 1
                indices += base + 2
                indices += base + 2
                indices += base + 3
                indices += base
            }
        }
    }
    return points to indices.toIntArray()
}

fun DrawPath.tessellateFill(): TriangleMesh {
    val contours = flattenContours()
    if (contours.isEmpty()) return TriangleMesh(emptyList(), IntArray(0))

    val points = ArrayList<DrawPoint>()
    val indices = ArrayList<Int>()
    resolveFillGroups(contours, fillRule).forEach { group ->
        appendGroupFill(group, onTriangulated = { meshPoints, meshIndices ->
            val base = points.size
            points += meshPoints
            meshIndices.forEach { indices += base + it }
        }, onFanned = { polygon ->
            appendCentroidFan(polygon, points, indices)
        })
    }
    return TriangleMesh(points, indices.toIntArray())
}

internal inline fun appendGroupFill(
    group: FillGroup,
    onTriangulated: (List<DrawPoint>, IntArray) -> Unit,
    onFanned: (List<DrawPoint>) -> Unit,
) {
    if (group.holes.isEmpty() && isConvex(group.outer)) {
        onFanned(group.outer)
        return
    }
    val (meshPoints, meshIndices) = scanlineFillTriangulation(
        contours = listOf(group.outer) + group.holes,
        fillRule = FillRule.NonZero,
    )
    if (meshIndices.isEmpty() && group.holes.isEmpty()) {
        onFanned(group.outer)
    } else {
        onTriangulated(meshPoints, meshIndices)
    }
}

internal fun appendCentroidFan(polygon: List<DrawPoint>, points: ArrayList<DrawPoint>, indices: ArrayList<Int>) {
    if (polygon.size < 3) return
    var centroidX = 0f
    var centroidY = 0f
    polygon.forEach {
        centroidX += it.x
        centroidY += it.y
    }
    centroidX /= polygon.size
    centroidY /= polygon.size

    val base = points.size
    points += DrawPoint(centroidX, centroidY)
    points += polygon
    for (i in 0 until polygon.size) {
        indices += base
        indices += base + 1 + i
        indices += base + 1 + (i + 1) % polygon.size
    }
}

const val AA_FRINGE_PX = 1f

fun DrawPath.tessellateFillAa(
    color: Color,
    fringePx: Float = AA_FRINGE_PX,
    insetPx: Float = fringePx / 2f,
): ColoredTriangleMesh {
    val contours = flattenContours()
    if (contours.isEmpty()) return ColoredTriangleMesh(emptyList(), IntArray(0))

    val vertices = ArrayList<ColoredVertex>()
    val indices = ArrayList<Int>()
    val transparent = color.withAlpha(0f)
    val inset = insetPx.coerceIn(0f, fringePx)
    val outset = fringePx - inset

    resolveFillGroups(contours, fillRule).forEach { group ->
        val insetOuter = offsetPolygon(group.outer, -inset)
        val outsetOuter = offsetPolygon(group.outer, outset)
        val insetHoles = group.holes.map { offsetPolygon(it, -inset) }
        val outsetHoles = group.holes.map { offsetPolygon(it, outset) }

        appendGroupFill(FillGroup(insetOuter, insetHoles), onTriangulated = { meshPoints, meshIndices ->
            val base = vertices.size
            meshPoints.forEach { vertices += ColoredVertex(it, color) }
            meshIndices.forEach { indices += base + it }
        }, onFanned = { polygon ->
            var centroidX = 0f
            var centroidY = 0f
            polygon.forEach {
                centroidX += it.x
                centroidY += it.y
            }
            centroidX /= polygon.size
            centroidY /= polygon.size

            val fanBase = vertices.size
            vertices += ColoredVertex(DrawPoint(centroidX, centroidY), color)
            polygon.forEach { vertices += ColoredVertex(it, color) }
            for (i in polygon.indices) {
                indices += fanBase
                indices += fanBase + 1 + i
                indices += fanBase + 1 + (i + 1) % polygon.size
            }
        })

        appendBoundaryFringe(insetOuter, outsetOuter, color, transparent, vertices, indices)
        for (i in group.holes.indices) {
            appendBoundaryFringe(insetHoles[i], outsetHoles[i], color, transparent, vertices, indices)
        }
    }
    return ColoredTriangleMesh(vertices, indices.toIntArray())
}

const val MAX_MITER_SCALE = 4f
private const val MITER_EPSILON = 1e-4f

fun offsetPolygon(polygon: List<DrawPoint>, distance: Float): List<DrawPoint> {
    val n = polygon.size
    if (n < 3) return polygon
    val outwardSign = if (polygonSignedArea(polygon) >= 0f) 1f else -1f

    fun edgeNormal(a: DrawPoint, b: DrawPoint): Pair<Float, Float> {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val length = hypot(dx, dy)
        if (length <= 0f) return 0f to 0f
        return (dy / length * outwardSign) to (-dx / length * outwardSign)
    }

    return polygon.indices.map { i ->
        val prev = polygon[(i - 1 + n) % n]
        val curr = polygon[i]
        val next = polygon[(i + 1) % n]
        val (prevNx, prevNy) = edgeNormal(prev, curr)
        val (nextNx, nextNy) = edgeNormal(curr, next)

        val sumX = prevNx + nextNx
        val sumY = prevNy + nextNy
        val sumLength = hypot(sumX, sumY)
        val (unitX, unitY, scale) = if (sumLength > 1e-4f) {
            val ux = sumX / sumLength
            val uy = sumY / sumLength
            val cosHalfAngle = prevNx * ux + prevNy * uy
            // A reversing or degenerate join has no stable miter direction. Use a bevel-sized
            // offset instead of allowing 1 / cosHalfAngle to poison the fringe mesh with NaN.
            val miterScale = if (cosHalfAngle > MITER_EPSILON) {
                (1f / cosHalfAngle).coerceAtMost(MAX_MITER_SCALE)
            } else {
                1f
            }
            Triple(ux, uy, miterScale)
        } else {
            Triple(prevNx, prevNy, 1f)
        }

        // Growing a convex corner outward cannot fold the ring back on itself, however short its
        // edges are; only the shrinking direction can, and at a reflex corner "shrinking" is
        // outward. Clamping both directions by adjacent edge length made the offset a function of
        // how finely the contour happened to be flattened -- an anti-aliased fringe on a rounded
        // shape came out at half the arc-segment length (0.003 px where 0.5 was asked for), so
        // every curve shipped effectively unantialiased.
        val crossZ = (curr.x - prev.x) * (next.y - curr.y) - (curr.y - prev.y) * (next.x - curr.x)
        val convex = crossZ * outwardSign > 0f
        val requested = distance * scale
        val offset = if ((distance >= 0f) == convex) {
            requested
        } else {
            val maxStep = 0.5f * min(
                hypot(curr.x - prev.x, curr.y - prev.y),
                hypot(next.x - curr.x, next.y - curr.y),
            )
            requested.coerceIn(-maxStep, maxStep)
        }
        DrawPoint(curr.x + unitX * offset, curr.y + unitY * offset)
    }
}

fun appendBoundaryFringe(
    innerRing: List<DrawPoint>,
    outerRing: List<DrawPoint>,
    color: Color,
    transparent: Color,
    vertices: ArrayList<ColoredVertex>,
    indices: ArrayList<Int>,
) {
    val n = innerRing.size
    if (n < 3 || outerRing.size != n) return
    val base = vertices.size
    for (i in 0 until n) {
        vertices += ColoredVertex(innerRing[i], color)
        vertices += ColoredVertex(outerRing[i], transparent)
    }
    for (i in 0 until n) {
        val next = (i + 1) % n
        val innerA = base + i * 2
        val outerA = innerA + 1
        val innerB = base + next * 2
        val outerB = innerB + 1
        indices += innerA
        indices += innerB
        indices += outerB
        indices += outerB
        indices += outerA
        indices += innerA
    }
}
