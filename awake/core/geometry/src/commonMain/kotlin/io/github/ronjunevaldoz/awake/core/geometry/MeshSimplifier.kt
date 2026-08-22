// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.geometry

import io.github.ronjunevaldoz.awake.core.math.Vec3d
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Garland-Heckbert quadric-error-metric edge-collapse mesh simplification -- position-only
 * (no UV/normal-seam awareness yet; a caller downsampling other per-vertex attributes uses
 * [Result.vertexRemap] to pick each surviving vertex's own attribute values, not a
 * barycentric re-projection -- see this file's own scope note below).
 *
 * Takes and returns the same `positions: FloatArray` + `indices: IntArray` shape
 * `MeshGeometry`/`GltfMesh` already use -- no new geometry representation, no dependency on
 * any asset format. Works on any mesh from any source.
 *
 * ponytail: attribute carry-over is nearest-vertex only (via [Result.vertexRemap]), not
 * re-interpolated; barycentric re-projection is the upgrade path once visual accuracy at
 * aggressive ratios actually matters.
 */
object MeshSimplifier {
    /**
     * Components per position in the flat `positions` array -- this is the vertex format
     * talking, so it derives from it. Any `* POSITION_COMPONENTS` below is indexing xyz.
     */
    private val POSITION_COMPONENTS = GpuDataShape.Vec3.componentCount

    /**
     * Vertices per triangle. Deliberately NOT [GpuDataShape.Vec3] wearing a different name:
     * this is topology, it is what "triangle list" means, and it would still be 3 if positions
     * were 2D or 4D. Collapsing the two into one constant compiles and passes, and is wrong.
     */
    private const val VERTICES_PER_TRIANGLE = 3

    private const val MIN_TRIANGLES = 4
    private const val DEGENERATE_NORMAL_LENGTH = 1e-12
    private const val SINGULAR_DETERMINANT = 1e-12
    private const val BOUNDARY_QUADRIC_WEIGHT = 1000.0

    /** Superseded heap entries tolerated per live candidate before [CandidateHeap.pop] compacts. */
    private const val COMPACT_RATIO = 4

    /**
     * [positions] reduced to their surviving vertices only, [indices] rewritten against them.
     * [vertexRemap] has one entry per ORIGINAL vertex (`vertexRemap.size ==
     * originalPositions.size / 3`): `vertexRemap[originalIndex]` is the index into this
     * result's own [positions] that original vertex collapsed into (or kept, if it survived
     * unmoved). A caller downsampling a parallel per-vertex array (normals, UVs, colors)
     * builds one representative original index per surviving vertex from this map, then reads
     * that original array by it.
     */
    data class Result(
        val positions: FloatArray,
        val indices: IntArray,
        val vertexRemap: IntArray,
    )

    /**
     * Simplifies [positions]/[indices] down to roughly `originalTriangleCount *
     * [targetTriangleRatio]` triangles (never below [MIN_TRIANGLES], never more than the
     * input has). `targetTriangleRatio` outside `0f..1f` is clamped, not an error -- `0f`
     * simplifies as aggressively as this algorithm can (down to [MIN_TRIANGLES]), `1f` (or
     * above) returns the input essentially unchanged (the collapse loop's target is already
     * met before it starts).
     */
    fun simplify(positions: FloatArray, indices: IntArray, targetTriangleRatio: Float): Result {
        val vertexCount = positions.size / POSITION_COMPONENTS
        val ratio = targetTriangleRatio.coerceIn(0f, 1f)
        val originalTriangleCount = indices.size / VERTICES_PER_TRIANGLE

        val working = DoubleArray(positions.size) { positions[it].toDouble() }
        val quadrics = Array(vertexCount) { DoubleArray(10) }
        val vertexTriangles = Array(vertexCount) { mutableSetOf<Int>() }
        val triangleAlive = BooleanArray(originalTriangleCount)
        val triangleVertices = IntArray(originalTriangleCount * VERTICES_PER_TRIANGLE) { indices[it] }
        val edgeTriangleCount = HashMap<Pair<Int, Int>, Int>()

        var validTriangleCount = 0
        for (t in 0 until originalTriangleCount) {
            val i0 = triangleVertices[t * VERTICES_PER_TRIANGLE]
            val i1 = triangleVertices[t * VERTICES_PER_TRIANGLE + 1]
            val i2 = triangleVertices[t * VERTICES_PER_TRIANGLE + 2]
            val plane = trianglePlane(working, i0, i1, i2) ?: continue
            triangleAlive[t] = true
            validTriangleCount += 1
            vertexTriangles[i0] += t
            vertexTriangles[i1] += t
            vertexTriangles[i2] += t
            addPlaneQuadric(quadrics[i0], plane)
            addPlaneQuadric(quadrics[i1], plane)
            addPlaneQuadric(quadrics[i2], plane)
            for (edge in triangleEdges(i0, i1, i2)) {
                edgeTriangleCount[edge] = (edgeTriangleCount[edge] ?: 0) + 1
            }
        }

        // Boundary edges (used by exactly one triangle) get a large penalty quadric on both
        // endpoints so an open mesh's border doesn't visibly shrink inward as interior
        // triangles collapse around it.
        for ((edge, count) in edgeTriangleCount) {
            if (count != 1) continue
            val boundary = boundaryQuadric(working, edge.first, edge.second) ?: continue
            val boundaryQuadricMatrix = planeOuterProduct(boundary)
            addWeightedQuadric(quadrics[edge.first], boundaryQuadricMatrix, BOUNDARY_QUADRIC_WEIGHT)
            addWeightedQuadric(quadrics[edge.second], boundaryQuadricMatrix, BOUNDARY_QUADRIC_WEIGHT)
        }

        val vertexAlive = BooleanArray(vertexCount) { true }
        val vertexOwner = IntArray(vertexCount) { it }
        val edgeCandidates = HashMap<Pair<Int, Int>, EdgeCandidate>()
        val heap = CandidateHeap()
        fun refreshEdge(a: Int, b: Int) {
            val key = edgeKey(a, b)
            val combined = DoubleArray(10)
            addWeightedQuadric(combined, quadrics[a], 1.0)
            addWeightedQuadric(combined, quadrics[b], 1.0)
            val target = solveOptimalPosition(combined) ?: midpoint(working, a, b)
            val candidate = EdgeCandidate(a, b, combined, target, quadricError(combined, target))
            edgeCandidates[key] = candidate
            heap.push(candidate)
        }
        for ((edge, _) in edgeTriangleCount) refreshEdge(edge.first, edge.second)

        var currentTriangleCount = validTriangleCount
        val targetTriangleCount = (validTriangleCount * ratio).roundToInt().coerceAtLeast(MIN_TRIANGLES)

        while (currentTriangleCount > targetTriangleCount) {
            val next = heap.pop(edgeCandidates, vertexAlive) ?: break
            edgeCandidates.remove(edgeKey(next.a, next.b))
            if (createsFlippedTriangle(working, vertexTriangles, triangleVertices, triangleAlive, next.a, next.b, next.target)) continue

            working[next.a * POSITION_COMPONENTS] = next.target.x
            working[next.a * POSITION_COMPONENTS + 1] = next.target.y
            working[next.a * POSITION_COMPONENTS + 2] = next.target.z
            quadrics[next.a] = next.quadric

            val survivorTriangles = vertexTriangles[next.a]
            for (t in vertexTriangles[next.b]) {
                if (!triangleAlive[t]) continue
                for (slot in 0 until VERTICES_PER_TRIANGLE) {
                    if (triangleVertices[t * VERTICES_PER_TRIANGLE + slot] == next.b) triangleVertices[t * VERTICES_PER_TRIANGLE + slot] = next.a
                }
                val i0 = triangleVertices[t * VERTICES_PER_TRIANGLE]
                val i1 = triangleVertices[t * VERTICES_PER_TRIANGLE + 1]
                val i2 = triangleVertices[t * VERTICES_PER_TRIANGLE + 2]
                if (i0 == i1 || i1 == i2 || i0 == i2) {
                    triangleAlive[t] = false
                    currentTriangleCount -= 1
                } else {
                    survivorTriangles += t
                }
            }
            vertexTriangles[next.b].clear()
            vertexAlive[next.b] = false
            vertexOwner[next.b] = next.a

            val neighbors = mutableSetOf<Int>()
            for (t in survivorTriangles) {
                if (!triangleAlive[t]) continue
                for (slot in 0 until VERTICES_PER_TRIANGLE) {
                    val v = triangleVertices[t * VERTICES_PER_TRIANGLE + slot]
                    if (v != next.a) neighbors += v
                }
            }
            for (neighbor in neighbors) {
                edgeCandidates.remove(edgeKey(neighbor, next.b))
                if (vertexAlive[neighbor]) refreshEdge(next.a, neighbor)
            }
        }

        return buildResult(working, vertexCount, vertexAlive, vertexOwner, triangleAlive, triangleVertices)
    }

    private data class EdgeCandidate(
        val a: Int,
        val b: Int,
        val quadric: DoubleArray,
        val target: Vec3d,
        val error: Double,
    )

    /**
     * Min-heap of collapse candidates ordered by quadric error, with lazy invalidation.
     *
     * A collapse re-scores every edge around the survivor, so an entry pushed earlier can be
     * stale by the time it surfaces. Rather than find and remove it -- a heap cannot do that
     * cheaply without an index-tracking side map -- [pop] discards any entry the caller's own
     * candidate map no longer points at, and any entry naming a vertex that has since been
     * collapsed away. Both checks are O(1), and a stale entry costs one sift, not a rescan.
     *
     * Superseded entries are therefore dead weight, and each one pins a quadric and a target
     * position. Left unbounded that outgrows the mesh itself -- a 65k-vertex grid exhausted a
     * default JVM heap before [compact] existed -- so [pop] drops them wholesale once they
     * outnumber the live candidates by [COMPACT_RATIO].
     */
    private class CandidateHeap {
        private val entries = ArrayList<EdgeCandidate>()

        fun push(candidate: EdgeCandidate) {
            entries.add(candidate)
            var child = entries.size - 1
            while (child > 0) {
                val parent = (child - 1) / 2
                if (entries[parent].error <= entries[child].error) break
                entries.swap(parent, child)
                child = parent
            }
        }

        /** Lowest-error candidate still live and still current, or `null` once none remain. */
        fun pop(current: Map<Pair<Int, Int>, EdgeCandidate>, vertexAlive: BooleanArray): EdgeCandidate? {
            if (entries.size > COMPACT_RATIO * current.size) compact(current, vertexAlive)
            while (entries.isNotEmpty()) {
                val top = entries[0]
                entries.swap(0, entries.size - 1)
                entries.removeAt(entries.size - 1)
                siftDown()
                val superseded = current[edgeKey(top.a, top.b)] !== top
                if (superseded || !vertexAlive[top.a] || !vertexAlive[top.b]) continue
                return top
            }
            return null
        }

        /** Drops every superseded/dead entry, then re-heapifies the survivors bottom-up. */
        private fun compact(current: Map<Pair<Int, Int>, EdgeCandidate>, vertexAlive: BooleanArray) {
            entries.retainAll { current[edgeKey(it.a, it.b)] === it && vertexAlive[it.a] && vertexAlive[it.b] }
            for (parent in entries.size / 2 - 1 downTo 0) siftDownFrom(parent)
        }

        private fun siftDown() = siftDownFrom(0)

        private fun siftDownFrom(start: Int) {
            var parent = start
            while (true) {
                val left = parent * 2 + 1
                val right = left + 1
                var smallest = parent
                if (left < entries.size && entries[left].error < entries[smallest].error) smallest = left
                if (right < entries.size && entries[right].error < entries[smallest].error) smallest = right
                if (smallest == parent) return
                entries.swap(parent, smallest)
                parent = smallest
            }
        }

        private fun ArrayList<EdgeCandidate>.swap(i: Int, j: Int) {
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    private fun edgeKey(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a

    private fun triangleEdges(i0: Int, i1: Int, i2: Int): List<Pair<Int, Int>> =
        listOf(edgeKey(i0, i1), edgeKey(i1, i2), edgeKey(i2, i0))

    /** `(nx, ny, nz, d)` for the plane through `i0,i1,i2` -- `null` for a degenerate
     * (zero-area) triangle, which contributes no quadric and isn't counted as valid. */
    private fun trianglePlane(p: DoubleArray, i0: Int, i1: Int, i2: Int): DoubleArray? {
        val v0 = Vec3d(p[i0 * POSITION_COMPONENTS], p[i0 * POSITION_COMPONENTS + 1], p[i0 * POSITION_COMPONENTS + 2])
        val v1 = Vec3d(p[i1 * POSITION_COMPONENTS], p[i1 * POSITION_COMPONENTS + 1], p[i1 * POSITION_COMPONENTS + 2])
        val v2 = Vec3d(p[i2 * POSITION_COMPONENTS], p[i2 * POSITION_COMPONENTS + 1], p[i2 * POSITION_COMPONENTS + 2])
        val a = v1 - v0
        val b = v2 - v0
        val n = a.cross(b)
        val length = n.length3()
        if (length < DEGENERATE_NORMAL_LENGTH) return null
        n.normalize()
        val d = -n.dot(v0)
        return doubleArrayOf(n.x, n.y, n.z, d)
    }

    /** A plane perpendicular to both the boundary edge `a-b` and to that edge's own direction
     * (a "fence" plane containing the edge, oriented along it) -- constrains a collapse
     * target from sliding the border inward. `null` for a zero-length edge. */
    private fun boundaryQuadric(p: DoubleArray, a: Int, b: Int): DoubleArray? {
        val va = Vec3d(p[a * POSITION_COMPONENTS], p[a * POSITION_COMPONENTS + 1], p[a * POSITION_COMPONENTS + 2])
        val vb = Vec3d(p[b * POSITION_COMPONENTS], p[b * POSITION_COMPONENTS + 1], p[b * POSITION_COMPONENTS + 2])
        val e = vb - va
        if (e.length3() < DEGENERATE_NORMAL_LENGTH) return null
        // Any vector perpendicular to the edge direction works as the fence's own normal --
        // pick one via a cross product with a not-parallel helper axis.
        val helper = if (abs(e.x) < abs(e.y)) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
        val n = e.cross(helper)
        if (n.length3() < DEGENERATE_NORMAL_LENGTH) return null
        n.normalize()
        val d = -n.dot(va)
        return doubleArrayOf(n.x, n.y, n.z, d)
    }

    private fun addPlaneQuadric(quadric: DoubleArray, plane: DoubleArray) = addWeightedQuadric(quadric, planeOuterProduct(plane), 1.0)

    private fun planeOuterProduct(plane: DoubleArray): DoubleArray {
        val (nx, ny, nz, d) = plane
        return doubleArrayOf(
            nx * nx, nx * ny, nx * nz, nx * d,
            ny * ny, ny * nz, ny * d,
            nz * nz, nz * d,
            d * d,
        )
    }

    private fun addWeightedQuadric(target: DoubleArray, source: DoubleArray, weight: Double) {
        for (i in target.indices) target[i] += source[i] * weight
    }

    /** `v^T Q v` for homogeneous `v = (x, y, z, 1)`, `Q` packed as
     * `[q0 q1 q2 q3; q1 q4 q5 q6; q2 q5 q7 q8; q3 q6 q8 q9]`. */
    private fun quadricError(q: DoubleArray, v: Vec3d): Double {
        val x = v.x
        val y = v.y
        val z = v.z
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x +
            q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y +
            q[7] * z * z + 2 * q[8] * z +
            q[9]
    }

    /** Minimizes `v^T Q v` by solving `Av = b` for the upper-left 3x3 block -- `null` when
     * that block is singular (or nearly so), so the caller falls back to the edge midpoint. */
    private fun solveOptimalPosition(q: DoubleArray): Vec3d? {
        val a00 = q[0]; val a01 = q[1]; val a02 = q[2]
        val a10 = q[1]; val a11 = q[4]; val a12 = q[5]
        val a20 = q[2]; val a21 = q[5]; val a22 = q[7]
        val bx = -q[3]; val by = -q[6]; val bz = -q[8]

        val det = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20)
        if (abs(det) < SINGULAR_DETERMINANT) return null

        val x = (bx * (a11 * a22 - a12 * a21) - a01 * (by * a22 - a12 * bz) + a02 * (by * a21 - a11 * bz)) / det
        val y = (a00 * (by * a22 - a12 * bz) - bx * (a10 * a22 - a12 * a20) + a02 * (a10 * bz - by * a20)) / det
        val z = (a00 * (a11 * bz - by * a21) - a01 * (a10 * bz - by * a20) + bx * (a10 * a21 - a11 * a20)) / det
        return Vec3d(x, y, z)
    }

    private fun midpoint(p: DoubleArray, a: Int, b: Int): Vec3d = Vec3d(
        (p[a * POSITION_COMPONENTS] + p[b * POSITION_COMPONENTS]) / 2.0,
        (p[a * POSITION_COMPONENTS + 1] + p[b * POSITION_COMPONENTS + 1]) / 2.0,
        (p[a * POSITION_COMPONENTS + 2] + p[b * POSITION_COMPONENTS + 2]) / 2.0,
    )

    /** `true` if moving [b] into [a] at [target] would flip any of [b]'s surviving (non-shared,
     * still-[triangleAlive]) triangles inside out -- compares each such triangle's normal
     * before and after substituting [b]'s position with [target]; a sign flip means this
     * collapse folds the mesh over itself, so the caller should reject this edge and try the
     * next-lowest-error one instead. Triangles shared by both [a] and [b] are skipped -- those
     * collapse away entirely, not a flip candidate. A triangle that's already degenerate
     * before or after (zero-area either way) is skipped too, not counted as a flip -- the
     * caller's own post-collapse degenerate filtering handles that case. */
    private fun createsFlippedTriangle(
        p: DoubleArray,
        vertexTriangles: Array<MutableSet<Int>>,
        triangleVertices: IntArray,
        triangleAlive: BooleanArray,
        a: Int,
        b: Int,
        target: Vec3d,
    ): Boolean {
        for (t in vertexTriangles[b]) {
            if (!triangleAlive[t]) continue
            val i0 = triangleVertices[t * VERTICES_PER_TRIANGLE]
            val i1 = triangleVertices[t * VERTICES_PER_TRIANGLE + 1]
            val i2 = triangleVertices[t * VERTICES_PER_TRIANGLE + 2]
            if (i0 == a || i1 == a || i2 == a) continue

            val oldNormal = trianglePlane(p, i0, i1, i2) ?: continue
            val newNormal = triangleNormalSubstituting(p, i0, i1, i2, b, target) ?: continue
            val dot = oldNormal[0] * newNormal.x + oldNormal[1] * newNormal.y + oldNormal[2] * newNormal.z
            if (dot < 0.0) return true
        }
        return false
    }

    /** Same normal computation [trianglePlane] does, but vertex [substituted]'s coordinates
     * come from [replacement] instead of [p] -- used to preview a triangle's normal after a
     * collapse without mutating [p] first. */
    private fun triangleNormalSubstituting(
        p: DoubleArray,
        i0: Int,
        i1: Int,
        i2: Int,
        substituted: Int,
        replacement: Vec3d,
    ): Vec3d? {
        fun pos(i: Int) = if (i == substituted) {
            replacement
        } else {
            Vec3d(p[i * POSITION_COMPONENTS], p[i * POSITION_COMPONENTS + 1], p[i * POSITION_COMPONENTS + 2])
        }
        val v0 = pos(i0)
        val a = pos(i1) - v0
        val b = pos(i2) - v0
        val n = a.cross(b)
        val length = n.length3()
        if (length < DEGENERATE_NORMAL_LENGTH) return null
        return n.normalize()
    }

    private fun buildResult(
        working: DoubleArray,
        vertexCount: Int,
        vertexAlive: BooleanArray,
        vertexOwner: IntArray,
        triangleAlive: BooleanArray,
        triangleVertices: IntArray,
    ): Result {
        fun root(index: Int): Int {
            var current = index
            while (vertexOwner[current] != current) current = vertexOwner[current]
            return current
        }

        val newIndexOf = IntArray(vertexCount) { -1 }
        var nextIndex = 0
        for (i in 0 until vertexCount) {
            if (!vertexAlive[i]) continue
            newIndexOf[i] = nextIndex
            nextIndex += 1
        }

        val newPositions = FloatArray(nextIndex * POSITION_COMPONENTS)
        for (i in 0 until vertexCount) {
            if (!vertexAlive[i]) continue
            val newIndex = newIndexOf[i]
            newPositions[newIndex * POSITION_COMPONENTS] = working[i * POSITION_COMPONENTS].toFloat()
            newPositions[newIndex * POSITION_COMPONENTS + 1] = working[i * POSITION_COMPONENTS + 1].toFloat()
            newPositions[newIndex * POSITION_COMPONENTS + 2] = working[i * POSITION_COMPONENTS + 2].toFloat()
        }

        val newIndices = ArrayList<Int>(triangleAlive.size * VERTICES_PER_TRIANGLE)
        for (t in triangleAlive.indices) {
            if (!triangleAlive[t]) continue
            val i0 = newIndexOf[root(triangleVertices[t * VERTICES_PER_TRIANGLE])]
            val i1 = newIndexOf[root(triangleVertices[t * VERTICES_PER_TRIANGLE + 1])]
            val i2 = newIndexOf[root(triangleVertices[t * VERTICES_PER_TRIANGLE + 2])]
            if (i0 == i1 || i1 == i2 || i0 == i2) continue
            newIndices += i0
            newIndices += i1
            newIndices += i2
        }

        val vertexRemap = IntArray(vertexCount) { newIndexOf[root(it)] }
        return Result(newPositions, newIndices.toIntArray(), vertexRemap)
    }
}
