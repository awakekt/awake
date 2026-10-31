/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlin.math.max
import kotlin.math.sqrt

/**
 * The simplifier's working mesh. Vertices that share a position are welded into one *point*; a point's
 * vertices are its *wedges* (one per side of a UV seam or hard edge). Triangles index wedges, and each
 * point lists the triangles around it. Positions are scaled so the largest extent is 1, which makes
 * errors and attribute weights independent of the model's size.
 */
internal class MeshTopology(positions: FloatArray, indices: IntArray, options: SimplifyOptions) {
    val wedgeCount: Int = positions.size / POSITION_COMPONENTS
    val pointOf = IntArray(wedgeCount)
    val pointCount: Int
    val points: DoubleArray
    val triangles: IntArray = indices.copyOf()
    val triangleAlive = BooleanArray(indices.size / TRIANGLE_CORNERS)
    var aliveTriangles = 0
        private set
    val incident: Array<IntList>
    val pointAlive: BooleanArray
    val border: BooleanArray
    val locked: BooleanArray
    val quadrics: DoubleArray

    init {
        val welded = HashMap<WeldKey, Int>()
        for (v in 0 until wedgeCount) {
            val key = WeldKey(positions[v * 3] + 0f, positions[v * 3 + 1] + 0f, positions[v * 3 + 2] + 0f)
            pointOf[v] = welded.getOrPut(key) { welded.size }
        }
        pointCount = welded.size
        points = DoubleArray(pointCount * 3)
        normalize(positions)
        incident = Array(pointCount) { IntList() }
        pointAlive = BooleanArray(pointCount) { true }
        for (t in triangleAlive.indices) {
            val a = pointOf[triangles[t * 3]]
            val b = pointOf[triangles[t * 3 + 1]]
            val c = pointOf[triangles[t * 3 + 2]]
            if (a == b || b == c || a == c) continue
            triangleAlive[t] = true
            aliveTriangles++
            incident[a].add(t)
            incident[b].add(t)
            incident[c].add(t)
        }
        border = BooleanArray(pointCount)
        locked = BooleanArray(pointCount)
        quadrics = DoubleArray(pointCount * Quadric.SIZE)
        classifyEdges(options)
    }

    fun point(t: Int, corner: Int): Int = pointOf[triangles[t * 3 + corner]]

    fun hasPoint(t: Int, p: Int): Boolean = point(t, 0) == p || point(t, 1) == p || point(t, 2) == p

    fun kill(t: Int) {
        triangleAlive[t] = false
        aliveTriangles--
    }

    /** The alive triangles around [p], pruning dead ones from its list as a side effect. */
    fun aliveIncident(p: Int): IntList = incident[p].also { list -> list.retain { triangleAlive[it] } }

    /** Adds the points sharing an alive triangle with [p] to [out], each once, using [marks]. */
    fun neighbours(p: Int, out: IntList, marks: Marks) {
        out.clear()
        val stamp = marks.next()
        marks.set(p, stamp)
        val list = aliveIncident(p)
        for (i in 0 until list.size) {
            val t = list[i]
            for (corner in 0 until 3) {
                val q = point(t, corner)
                if (!marks.isSet(q, stamp)) {
                    marks.set(q, stamp)
                    out.add(q)
                }
            }
        }
    }

    private fun normalize(positions: FloatArray) {
        val min = DoubleArray(3) { Double.MAX_VALUE }
        val maxCorner = DoubleArray(3) { -Double.MAX_VALUE }
        for (v in 0 until wedgeCount) {
            for (axis in 0 until 3) {
                val value = positions[v * 3 + axis].toDouble()
                if (value < min[axis]) min[axis] = value
                if (value > maxCorner[axis]) maxCorner[axis] = value
            }
        }
        var extent = 0.0
        for (axis in 0 until 3) extent = max(extent, maxCorner[axis] - min[axis])
        val scale = if (extent > 0.0) 1.0 / extent else 1.0
        for (v in 0 until wedgeCount) {
            for (axis in 0 until 3) {
                points[pointOf[v] * 3 + axis] = (positions[v * 3 + axis] - min[axis]) * scale
            }
        }
    }

    /** Counts the triangles on each point edge: a border has one, a non-manifold edge more than two. */
    private fun classifyEdges(options: SimplifyOptions) {
        val counts = HashMap<Long, Int>()
        for (t in triangleAlive.indices) {
            if (!triangleAlive[t]) continue
            Quadric.addPlane(quadrics, points, point(t, 0), point(t, 1), point(t, 2))
            for (corner in 0 until 3) {
                val key = edgeKey(point(t, corner), point(t, (corner + 1) % 3))
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        for ((key, count) in counts) {
            val a = (key ushr 32).toInt()
            val b = key.toInt()
            when {
                count == 1 -> {
                    border[a] = true
                    border[b] = true
                    addBorderPlane(a, b, options.borderWeight.toDouble())
                }
                count > 2 -> {
                    locked[a] = true
                    locked[b] = true
                }
            }
        }
        if (options.lockBorders) for (p in 0 until pointCount) if (border[p]) locked[p] = true
    }

    /** A plane through the border edge [a]-[b], perpendicular to its triangle, so the border keeps its line. */
    @Suppress("ReturnCount")
    private fun addBorderPlane(a: Int, b: Int, weight: Double) {
        val list = incident[a]
        for (i in 0 until list.size) {
            val t = list[i]
            if (!hasPoint(t, b)) continue
            val c = (0 until 3).map { point(t, it) }.first { it != a && it != b }
            val n = Quadric.normal(points, a, b, c) ?: return
            val ex = points[b * 3] - points[a * 3]
            val ey = points[b * 3 + 1] - points[a * 3 + 1]
            val ez = points[b * 3 + 2] - points[a * 3 + 2]
            var px = ey * n[2] - ez * n[1]
            var py = ez * n[0] - ex * n[2]
            var pz = ex * n[1] - ey * n[0]
            val length = sqrt(px * px + py * py + pz * pz)
            if (length == 0.0) return
            px /= length
            py /= length
            pz /= length
            val d = -(px * points[a * 3] + py * points[a * 3 + 1] + pz * points[a * 3 + 2])
            Quadric.add(quadrics, a, px, py, pz, d, weight)
            Quadric.add(quadrics, b, px, py, pz, d, weight)
            return
        }
    }

    private data class WeldKey(val x: Float, val y: Float, val z: Float)
}

/** An unordered point pair as one key. */
internal fun edgeKey(a: Int, b: Int): Long =
    if (a < b) (a.toLong() shl 32) or (b.toLong() and 0xffffffffL) else (b.toLong() shl 32) or (a.toLong() and 0xffffffffL)
