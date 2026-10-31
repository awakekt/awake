/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The collapse loop: every point edge is a candidate in both directions, the cheapest valid collapse
 * goes first, and the points around a collapse are re-evaluated after it.
 */
// The collapse loop's checks read best as early outs, and its hot loop avoids helper allocations.
@Suppress("CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
internal class EdgeCollapse(
    private val mesh: MeshTopology,
    private val vertexCost: VertexCost,
    private val options: SimplifyOptions,
) {
    private val version = IntArray(mesh.pointCount)
    private val heap = CollapseHeap()
    private val marks = Marks(mesh.pointCount)
    private val around = IntList()
    private val aroundFrom = IntList()
    private val aroundTo = IntList()
    private val mapFrom = IntArray(MAX_WEDGES)
    private val mapTo = IntArray(MAX_WEDGES)
    private var mapCount = 0

    fun run(): SimplifiedMesh {
        val target = (mesh.aliveTriangles * options.targetRatio).roundToInt()
        val maxCost = if (options.maxError == Float.MAX_VALUE) Double.MAX_VALUE else options.maxError.toDouble().let { it * it }
        for (p in 0 until mesh.pointCount) {
            mesh.neighbours(p, around, marks)
            for (i in 0 until around.size) if (around[i] > p) pushBest(p, around[i])
        }
        var worst = 0.0
        while (mesh.aliveTriangles > target && heap.size > 0) {
            heap.pop()
            val v = heap.topFrom
            val u = heap.topTo
            if (!mesh.pointAlive[v] || !mesh.pointAlive[u]) continue
            if (version[v] != heap.topFromVersion || version[u] != heap.topToVersion) continue
            val cost = evaluate(v, u)
            if (cost == Double.POSITIVE_INFINITY) continue
            if (cost > heap.topCost * STALE_TOLERANCE + STALE_EPSILON) {
                heap.push(cost, v, u, version[v], version[u])
                continue
            }
            if (cost > maxCost) break
            collapse(v, u)
            worst = max(worst, cost)
        }
        return SimplifiedMesh(aliveIndices(), sqrt(worst).toFloat())
    }

    private fun pushBest(a: Int, b: Int) {
        val ab = evaluate(a, b)
        val ba = evaluate(b, a)
        when {
            ab == Double.POSITIVE_INFINITY && ba == Double.POSITIVE_INFINITY -> Unit
            ab <= ba -> heap.push(ab, a, b, version[a], version[b])
            else -> heap.push(ba, b, a, version[b], version[a])
        }
    }

    /** The cost of moving point [v] onto point [u], or infinity when that collapse isn't allowed. */
    private fun evaluate(v: Int, u: Int): Double {
        if (mesh.locked[v]) return Double.POSITIVE_INFINITY
        val list = mesh.aliveIncident(v)
        mapCount = 0
        var shared = 0
        for (i in 0 until list.size) {
            val t = list[i]
            if (!mesh.hasPoint(t, u)) continue
            shared++
            if (!addMapping(wedgeAt(t, v), wedgeAt(t, u))) return Double.POSITIVE_INFINITY
        }
        val edgeAllowed = if (mesh.border[v]) shared == 1 && mesh.border[u] else shared == 2
        if (!edgeAllowed) return Double.POSITIVE_INFINITY
        for (i in 0 until list.size) {
            val t = list[i]
            if (mesh.hasPoint(t, u)) continue
            // A wedge of v that no shared triangle maps would leave its seam side behind: the seam tears.
            if (mapped(wedgeAt(t, v)) < 0 || flips(t, v, u)) return Double.POSITIVE_INFINITY
        }
        if (!keepsTopology(v, u, shared)) return Double.POSITIVE_INFINITY
        var attributeCost = 0.0
        var scale = 1.0
        for (i in 0 until mapCount) {
            attributeCost += vertexCost.between(mapFrom[i], mapTo[i])
            scale = max(scale, vertexCost.scale(mapFrom[i]))
        }
        return (Quadric.error(mesh.quadrics, v, mesh.points, u) + attributeCost * list.size) * scale
    }

    private fun collapse(v: Int, u: Int) {
        val list = mesh.aliveIncident(v)
        for (i in 0 until list.size) {
            val t = list[i]
            if (mesh.hasPoint(t, u)) {
                mesh.kill(t)
                continue
            }
            for (corner in 0 until TRIANGLE_CORNERS) {
                val wedge = mesh.triangles[t * 3 + corner]
                if (mesh.pointOf[wedge] == v) mesh.triangles[t * 3 + corner] = mapped(wedge)
            }
            mesh.incident[u].add(t)
        }
        list.clear()
        mesh.pointAlive[v] = false
        Quadric.merge(mesh.quadrics, v, u)
        version[u]++
        mesh.neighbours(u, around, marks)
        for (i in 0 until around.size) pushBest(u, around[i])
    }

    /** The surface stays a manifold: the points [v] and [u] share are exactly their shared triangles' third points. */
    private fun keepsTopology(v: Int, u: Int, shared: Int): Boolean {
        mesh.neighbours(v, aroundFrom, marks)
        mesh.neighbours(u, aroundTo, marks)
        val stamp = marks.next()
        for (i in 0 until aroundFrom.size) marks.set(aroundFrom[i], stamp)
        var common = 0
        for (i in 0 until aroundTo.size) {
            val q = aroundTo[i]
            if (q != v && marks.isSet(q, stamp)) common++
        }
        return common == shared
    }

    /** Whether moving [v] onto [u] turns triangle [t] over, or nearly so. */
    private fun flips(t: Int, v: Int, u: Int): Boolean {
        val a = mesh.point(t, 0)
        val b = mesh.point(t, 1)
        val c = mesh.point(t, 2)
        val before = Quadric.cross(mesh.points, a, b, c, -1, -1)
        val after = Quadric.cross(mesh.points, a, b, c, v, u)
        val dot = before[0] * after[0] + before[1] * after[1] + before[2] * after[2]
        val lengths = sqrt(before[0] * before[0] + before[1] * before[1] + before[2] * before[2]) *
            sqrt(after[0] * after[0] + after[1] * after[1] + after[2] * after[2])
        return dot <= FLIP_THRESHOLD * lengths
    }

    private fun wedgeAt(t: Int, p: Int): Int {
        for (corner in 0 until TRIANGLE_CORNERS) {
            val wedge = mesh.triangles[t * 3 + corner]
            if (mesh.pointOf[wedge] == p) return wedge
        }
        error("triangle $t has no corner at point $p")
    }

    private fun addMapping(from: Int, to: Int): Boolean {
        for (i in 0 until mapCount) if (mapFrom[i] == from) return mapTo[i] == to
        if (mapCount == MAX_WEDGES) return false
        mapFrom[mapCount] = from
        mapTo[mapCount] = to
        mapCount++
        return true
    }

    private fun mapped(wedge: Int): Int {
        for (i in 0 until mapCount) if (mapFrom[i] == wedge) return mapTo[i]
        return -1
    }

    private fun aliveIndices(): IntArray {
        val out = IntArray(mesh.aliveTriangles * TRIANGLE_CORNERS)
        var next = 0
        for (t in mesh.triangleAlive.indices) {
            if (!mesh.triangleAlive[t]) continue
            for (corner in 0 until TRIANGLE_CORNERS) out[next++] = mesh.triangles[t * 3 + corner]
        }
        return out
    }

    private companion object {
        const val MAX_WEDGES = 16
        const val FLIP_THRESHOLD = 0.2
        const val STALE_TOLERANCE = 1.000001
        const val STALE_EPSILON = 1e-30
    }
}
