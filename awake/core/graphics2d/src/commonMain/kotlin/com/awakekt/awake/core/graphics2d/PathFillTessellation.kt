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

/**
 * Represents a polygon fill group consisting of an outer contour boundary and zero or more interior hole contours.
 *
 * @property outer The outer boundary points defining the exterior perimeter of the polygon.
 * @property holes The list of inner hole contours subtracted from the outer boundary.
 */
class FillGroup(
    val outer: List<DrawPoint>,
    val holes: List<List<DrawPoint>>,
)

typealias UiFillGroup = FillGroup

/**
 * Resolves a collection of path contours into structured [FillGroup] instances according to the given [fillRule].
 *
 * @param contours The raw path contours to classify and group.
 * @param fillRule The winding rule used to determine fill inclusion and hole subtraction.
 * @return A list of [FillGroup]s representing the decomposed solid regions and holes.
 */
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

/**
 * Performs scanline trapezoidal decomposition and triangulation for a set of polygon contours.
 *
 * @param contours The list of closed polygon point lists to triangulate.
 * @param fillRule The fill rule determining which regions are inside the geometry.
 * @return A pair containing the generated vertex points and the triangle index array.
 */
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

/**
 * Tessellates this path into a triangulated 2D mesh according to its [DrawPath.fillRule].
 *
 * @return A [TriangleMesh] containing vertices and triangle indices for the filled path.
 */
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

/**
 * Default pixel width for anti-aliasing feathering fringes around path fills and strokes.
 */
const val AA_FRINGE_PX = 1f

/**
 * Tessellates this path into an anti-aliased colored triangle mesh with translucent boundary fringes.
 *
 * @param color The primary fill color of the mesh.
 * @param fringePx Total width in pixels of the anti-aliasing boundary fringe.
 * @param insetPx Distance in pixels by which the solid inner core is inset from the nominal boundary.
 * @return A [ColoredTriangleMesh] containing the solid core and feathered outer fringe.
 */
fun DrawPath.tessellateFillAa(
    color: Color,
    fringePx: Float = AA_FRINGE_PX,
    insetPx: Float = fringePx / 2f,
): ColoredTriangleMesh {
    val contours = flattenContours()
    if (contours.isEmpty()) return ColoredTriangleMesh(emptyList(), IntArray(0))

    val vertices = ArrayList<ColoredVertex>()
    val indices = ArrayList<Int>()
    // Every group's solid core goes in before any group's fringe. A fringe that reaches over a
    // neighbouring group's core -- one side of a partial border meeting the next -- would otherwise
    // reach a pixel first and leave a hole, since a mesh paints each pixel once.
    val fringeVertices = ArrayList<ColoredVertex>()
    val fringeIndices = ArrayList<Int>()
    val transparent = color.withAlpha(0f)
    val inset = insetPx.coerceIn(0f, fringePx)
    val outset = fringePx - inset

    resolveFillGroups(contours, fillRule).forEach { group ->
        val insetOuter = offsetPolygon(group.outer, -inset)
        val outsetOuter = offsetPolygon(group.outer, outset)
        // offsetPolygon moves a ring away from its own interior for a positive distance, and a
        // hole's interior is the empty side: shrinking the shape grows its holes.
        val insetHoles = group.holes.map { offsetPolygon(it, inset) }
        val outsetHoles = group.holes.map { offsetPolygon(it, -outset) }

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

        appendBoundaryFringe(insetOuter, outsetOuter, color, transparent, fringeVertices, fringeIndices)
        for (i in group.holes.indices) {
            appendBoundaryFringe(insetHoles[i], outsetHoles[i], color, transparent, fringeVertices, fringeIndices)
        }
    }
    val fringeBase = vertices.size
    vertices += fringeVertices
    fringeIndices.forEach { indices += fringeBase + it }
    return ColoredTriangleMesh(vertices, indices.toIntArray())
}

/**
 * Maximum miter scale factor permitted when expanding or insetting sharp corners during polygon offsetting.
 */
const val MAX_MITER_SCALE = 4f
internal const val MITER_EPSILON = 1e-4f

/**
 * Offsets a 2D polygon outward (positive [distance]) or inward (negative [distance]) along corner bisectors.
 *
 * @param polygon The vertices defining the closed polygon.
 * @param distance The signed distance by which to offset each edge.
 * @return A new list of [DrawPoint]s representing the offset polygon boundary.
 */
fun offsetPolygon(polygon: List<DrawPoint>, distance: Float): List<DrawPoint> {
    val n = polygon.size
    if (n < 3) return polygon
    val outwardSign = if (polygonSignedArea(polygon) >= 0f) 1f else -1f
    // A ring can repeat a point -- a stroke outline closes on a start cap whose last point lands a
    // float epsilon from the first. That sliver edge has a normal in a random direction, which
    // corrupted the fringe along the whole edge it touched. Measure every corner against the
    // nearest distinct neighbour instead; repeated points then offset to the same place.
    fun nearestDistinct(i: Int, step: Int): Int {
        var j = (i + step + n) % n
        while (j != i && hypot(polygon[j].x - polygon[i].x, polygon[j].y - polygon[i].y) <= DEGENERATE_EDGE_PX) {
            j = (j + step + n) % n
        }
        return j
    }
    val prevOf = IntArray(n) { nearestDistinct(it, step = -1) }
    val nextOf = IntArray(n) { nearestDistinct(it, step = 1) }
    val foldTan = FloatArray(n) { i ->
        foldTangent(polygon[prevOf[i]], polygon[i], polygon[nextOf[i]], distance, outwardSign)
    }

    return polygon.indices.map { i ->
        val prev = polygon[prevOf[i]]
        val curr = polygon[i]
        val next = polygon[nextOf[i]]
        val (unitX, unitY, scale) = miterDirection(
            edgeNormal(prev, curr, outwardSign),
            edgeNormal(curr, next, outwardSign),
        )
        // Only a corner that folds inward under this offset is limited, and only by how far its
        // edges can slide before they invert. Half the adjacent edge length, the previous limit,
        // depends on how finely a curve was flattened: a 0.5 px inset on a round cap cut into
        // 0.14 px segments came out at 0.07 px, so the fringe sat outside every curved stroke and
        // made it heavier.
        val foldLimit = if (foldTan[i] == 0f) {
            Float.MAX_VALUE
        } else {
            min(
                hypot(curr.x - prev.x, curr.y - prev.y) / (foldTan[prevOf[i]] + foldTan[i]),
                hypot(next.x - curr.x, next.y - curr.y) / (foldTan[i] + foldTan[nextOf[i]]),
            )
        }
        val offset = distance.coerceIn(-foldLimit, foldLimit) * scale
        DrawPoint(curr.x + unitX * offset, curr.y + unitY * offset)
    }
}

/** Points closer than this are one point to [offsetPolygon]: far below a pixel, far above float noise. */
private const val DEGENERATE_EDGE_PX = 1e-3f

/**
 * Appends a triangular fringe mesh connecting an inner ring to an outer ring, fading from [color] to [transparent].
 *
 * @param innerRing The vertices of the inner polygon ring.
 * @param outerRing The corresponding vertices of the outer polygon ring.
 * @param color The color assigned to the inner ring vertices.
 * @param transparent The color assigned to the outer ring vertices.
 * @param vertices The output vertex list to which generated fringe vertices are appended.
 * @param indices The output index list to which generated triangle indices are appended.
 */
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
