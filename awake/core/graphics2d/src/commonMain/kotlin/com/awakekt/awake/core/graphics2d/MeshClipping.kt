/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import kotlin.math.abs

/**
 * Clipping a mesh against a convex contour.
 *
 * Split out of `PathFillTessellation.kt`, where it was the larger half and shared nothing with fill
 * tessellation beyond the geometry predicates both use.
 */

fun DrawPath.convexClipContour(): List<DrawPoint>? {
    val contour = flattenContours().firstOrNull { it.closed && it.points.size >= 3 }?.points ?: return null
    return if (isConvex(contour)) contour else null
}

fun TriangleMesh.clipToConvexPath(path: DrawPath): TriangleMesh {
    val clipContour = path.convexClipContour() ?: return this
    return clipToConvexContour(clipContour)
}

fun TexturedTriangleMesh.clipToConvexPath(path: DrawPath): TexturedTriangleMesh {
    val clipContour = path.convexClipContour() ?: return this
    return clipToConvexContour(clipContour)
}

fun TriangleMesh.clipToConvexPaths(paths: List<DrawPath>): TriangleMesh {
    var current = this
    paths.forEach { path ->
        val contour = path.convexClipContour() ?: return current
        current = current.clipToConvexContour(contour)
    }
    return current
}

fun TexturedTriangleMesh.clipToConvexPaths(paths: List<DrawPath>): TexturedTriangleMesh {
    var current = this
    paths.forEach { path ->
        val contour = path.convexClipContour() ?: return current
        current = current.clipToConvexContour(contour)
    }
    return current
}

fun ColoredTriangleMesh.clipToConvexPaths(paths: List<DrawPath>): ColoredTriangleMesh {
    var current = this
    paths.forEach { path ->
        val contour = path.convexClipContour() ?: return current
        current = current.clipToConvexContour(contour)
    }
    return current
}

private fun TriangleMesh.clipToConvexContour(clipContour: List<DrawPoint>): TriangleMesh {
    if (points.isEmpty() || indices.isEmpty()) return this

    val clippedPoints = ArrayList<DrawPoint>()
    val clippedIndices = ArrayList<Int>()
    var index = 0
    while (index + 2 < indices.size) {
        val triangle = listOf(
            points[indices[index]],
            points[indices[index + 1]],
            points[indices[index + 2]],
        )
        val clippedPolygon = clipPolygonToConvexContour(triangle, clipContour)
        if (clippedPolygon.size >= 3) {
            val base = clippedPoints.size
            clippedPoints += clippedPolygon
            for (i in 1 until clippedPolygon.lastIndex) {
                clippedIndices += base
                clippedIndices += base + i
                clippedIndices += base + i + 1
            }
        }
        index += 3
    }
    return TriangleMesh(clippedPoints, clippedIndices.toIntArray())
}

private fun TexturedTriangleMesh.clipToConvexContour(clipContour: List<DrawPoint>): TexturedTriangleMesh {
    if (vertices.isEmpty() || indices.isEmpty()) return this

    val clippedVertices = ArrayList<TexturedVertex>()
    val clippedIndices = ArrayList<Int>()
    var index = 0
    while (index + 2 < indices.size) {
        val triangle = listOf(
            vertices[indices[index]],
            vertices[indices[index + 1]],
            vertices[indices[index + 2]],
        )
        val clippedPolygon = clipTexturedPolygonToConvexContour(triangle, clipContour)
        if (clippedPolygon.size >= 3) {
            val base = clippedVertices.size
            clippedVertices += clippedPolygon
            for (i in 1 until clippedPolygon.lastIndex) {
                clippedIndices += base
                clippedIndices += base + i
                clippedIndices += base + i + 1
            }
        }
        index += 3
    }
    return TexturedTriangleMesh(clippedVertices, clippedIndices.toIntArray())
}

private fun ColoredTriangleMesh.clipToConvexContour(clipContour: List<DrawPoint>): ColoredTriangleMesh {
    if (vertices.isEmpty() || indices.isEmpty()) return this

    val clippedVertices = ArrayList<ColoredVertex>()
    val clippedIndices = ArrayList<Int>()
    var index = 0
    while (index + 2 < indices.size) {
        val triangle = listOf(
            vertices[indices[index]],
            vertices[indices[index + 1]],
            vertices[indices[index + 2]],
        )
        val clippedPolygon = clipColoredPolygonToConvexContour(triangle, clipContour)
        if (clippedPolygon.size >= 3) {
            val base = clippedVertices.size
            clippedVertices += clippedPolygon
            for (i in 1 until clippedPolygon.lastIndex) {
                clippedIndices += base
                clippedIndices += base + i
                clippedIndices += base + i + 1
            }
        }
        index += 3
    }
    return ColoredTriangleMesh(clippedVertices, clippedIndices.toIntArray())
}

private fun clipPolygonToConvexContour(subject: List<DrawPoint>, clip: List<DrawPoint>): List<DrawPoint> {
    if (subject.isEmpty()) return emptyList()
    var output = subject
    val orientation = polygonSignedArea(clip)
    if (orientation == 0f) return subject
    val isCounterClockwise = orientation > 0f

    for (i in clip.indices) {
        val a = clip[i]
        val b = clip[(i + 1) % clip.size]
        if (output.isEmpty()) break
        val input = output
        // A convex clip contour flattened from an arc (a rounded rect's corners) can carry 20-30
        // edges, but any one small polygon is only ever near one or two of them -- most edges
        // leave every point inside and would rebuild an identical list. Skipping the rebuild when
        // nothing is outside turns that into a handful of cheap cross-product checks instead of
        // an allocation + copy, without changing the result.
        if (input.all { isInsideConvexEdge(it, a, b, isCounterClockwise) }) continue
        output = buildList {
            var prev = input.last()
            input.forEach { curr ->
                val currInside = isInsideConvexEdge(curr, a, b, isCounterClockwise)
                val prevInside = isInsideConvexEdge(prev, a, b, isCounterClockwise)
                if (currInside) {
                    if (!prevInside) add(lineIntersection(prev, curr, a, b))
                    add(curr)
                } else if (prevInside) {
                    add(lineIntersection(prev, curr, a, b))
                }
                prev = curr
            }
        }
    }
    return output
}

private fun clipTexturedPolygonToConvexContour(subject: List<TexturedVertex>, clip: List<DrawPoint>): List<TexturedVertex> {
    if (subject.isEmpty()) return emptyList()
    var output = subject
    val orientation = polygonSignedArea(clip)
    if (orientation == 0f) return subject
    val isCounterClockwise = orientation > 0f

    for (i in clip.indices) {
        val a = clip[i]
        val b = clip[(i + 1) % clip.size]
        if (output.isEmpty()) break
        val input = output
        // See the plain-point clipPolygonToConvexContour's comment: most edges of a many-sided
        // convex contour leave every point inside and would rebuild an unchanged list.
        if (input.all { isInsideConvexEdge(it.position, a, b, isCounterClockwise) }) continue
        output = buildList {
            var prev = input.last()
            input.forEach { curr ->
                val currInside = isInsideConvexEdge(curr.position, a, b, isCounterClockwise)
                val prevInside = isInsideConvexEdge(prev.position, a, b, isCounterClockwise)
                if (currInside) {
                    if (!prevInside) add(lineIntersection(prev, curr, a, b))
                    add(curr)
                } else if (prevInside) {
                    add(lineIntersection(prev, curr, a, b))
                }
                prev = curr
            }
        }
    }
    return output
}

private fun clipColoredPolygonToConvexContour(subject: List<ColoredVertex>, clip: List<DrawPoint>): List<ColoredVertex> {
    if (subject.isEmpty()) return emptyList()
    var output = subject
    val orientation = polygonSignedArea(clip)
    if (orientation == 0f) return subject
    val isCounterClockwise = orientation > 0f

    for (i in clip.indices) {
        val a = clip[i]
        val b = clip[(i + 1) % clip.size]
        if (output.isEmpty()) break
        val input = output
        // See the plain-point clipPolygonToConvexContour's comment: most edges of a many-sided
        // convex contour leave every point inside and would rebuild an unchanged list. This is
        // the hot path (profiled: dominates `exactClipColored` under a whole-shell rounded clip).
        if (input.all { isInsideConvexEdge(it.position, a, b, isCounterClockwise) }) continue
        output = buildList {
            var prev = input.last()
            input.forEach { curr ->
                val currInside = isInsideConvexEdge(curr.position, a, b, isCounterClockwise)
                val prevInside = isInsideConvexEdge(prev.position, a, b, isCounterClockwise)
                if (currInside) {
                    if (!prevInside) add(lineIntersection(prev, curr, a, b))
                    add(curr)
                } else if (prevInside) {
                    add(lineIntersection(prev, curr, a, b))
                }
                prev = curr
            }
        }
    }
    return output
}

private fun isInsideConvexEdge(point: DrawPoint, edgeStart: DrawPoint, edgeEnd: DrawPoint, counterClockwise: Boolean): Boolean {
    val cross = isLeft(edgeStart, edgeEnd, point.x, point.y)
    return if (counterClockwise) cross >= 0f else cross <= 0f
}

private fun lineIntersection(p1: DrawPoint, p2: DrawPoint, a: DrawPoint, b: DrawPoint): DrawPoint {
    val s1x = p2.x - p1.x
    val s1y = p2.y - p1.y
    val s2x = b.x - a.x
    val s2y = b.y - a.y
    val denominator = -s2x * s1y + s1x * s2y
    if (denominator == 0f) return p2
    val s = (-s1y * (p1.x - a.x) + s1x * (p1.y - a.y)) / denominator
    return DrawPoint(a.x + s * s2x, a.y + s * s2y)
}

private fun lineIntersection(p1: TexturedVertex, p2: TexturedVertex, a: DrawPoint, b: DrawPoint): TexturedVertex {
    val intersection = lineIntersection(p1.position, p2.position, a, b)
    val dx = p2.position.x - p1.position.x
    val dy = p2.position.y - p1.position.y
    val t = when {
        abs(dx) >= abs(dy) && dx != 0f -> (intersection.x - p1.position.x) / dx
        dy != 0f -> (intersection.y - p1.position.y) / dy
        else -> 0f
    }.coerceIn(0f, 1f)
    return TexturedVertex(
        position = intersection,
        u = p1.u + (p2.u - p1.u) * t,
        v = p1.v + (p2.v - p1.v) * t,
    )
}

private fun lineIntersection(p1: ColoredVertex, p2: ColoredVertex, a: DrawPoint, b: DrawPoint): ColoredVertex {
    val intersection = lineIntersection(p1.position, p2.position, a, b)
    val dx = p2.position.x - p1.position.x
    val dy = p2.position.y - p1.position.y
    val t = when {
        abs(dx) >= abs(dy) && dx != 0f -> (intersection.x - p1.position.x) / dx
        dy != 0f -> (intersection.y - p1.position.y) / dy
        else -> 0f
    }.coerceIn(0f, 1f)
    return ColoredVertex(
        position = intersection,
        color = p1.color.lerp(p2.color, t),
    )
}
