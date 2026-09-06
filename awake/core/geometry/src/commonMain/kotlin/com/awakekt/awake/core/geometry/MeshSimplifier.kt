/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import com.awakekt.awake.core.math.Vec3d
import kotlin.math.roundToInt

/**
 * Garland-Heckbert quadric-error-metric (QEM) edge-collapse mesh simplification (equivalent to
 * Sven Forstmann's Fast-Quadric-Mesh-Simplification, implemented in 100% pure Kotlin Multiplatform).
 *
 * Takes flat `positions: FloatArray` + `indices: IntArray` buffers, calculates symmetric 10-element
 * quadric error matrices ($Q = n n^T + d^2$) per vertex, evaluates edge collapse costs using analytic
 * 3x3 matrix inversion, and iteratively collapses edges while preventing normal inversions/folds.
 *
 * Supports [lockBoundaries] to strictly pin open boundary loops (e.g. modular character seams like
 * necks, wrists, waists, ankles) at their exact 3D coordinates during decimation.
 *
 * ### Example Usage
 * ```kotlin
 * // Simplify mesh down to 50% triangle count with boundary seam protection
 * val result = MeshSimplifier.simplify(
 *     positions = meshPositions,
 *     indices = meshIndices,
 *     targetTriangleRatio = 0.50f,
 *     lockBoundaries = true
 * )
 *
 * val newPositions = result.positions
 * val newIndices = result.indices
 *
 * // Downsample parallel vertex attributes (UVs, Normals, Bone Weights) via vertexRemap
 * val newUvs = FloatArray((newPositions.size / 3) * 2)
 * for (origIdx in result.vertexRemap.indices) {
 *     val newIdx = result.vertexRemap[origIdx]
 *     newUvs[newIdx * 2] = oldUvs[origIdx * 2]
 *     newUvs[newIdx * 2 + 1] = oldUvs[origIdx * 2 + 1]
 * }
 * ```
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
object MeshSimplifier {
    private val POSITION_COMPONENTS = GpuDataShape.Vec3.componentCount
    private const val VERTICES_PER_TRIANGLE = 3
    private const val MIN_TRIANGLES = 4
    private const val DEGENERATE_NORMAL_LENGTH = 1e-12
    private const val SINGULAR_DETERMINANT = 1e-12
    private const val BOUNDARY_QUADRIC_WEIGHT = 1000.0
    private const val COMPACT_RATIO = 4

    /**
     * [positions] reduced to their surviving vertices only, [indices] rewritten against them.
     */
    data class Result(
        val positions: FloatArray,
        val indices: IntArray,
        val vertexRemap: IntArray,
    )

    /**
     * Simplifies [positions]/[indices] down to roughly `originalTriangleCount * [targetTriangleRatio]` triangles.
     *
     * @param positions Flat (x, y, z) vertex positions.
     * @param indices Flat triangle vertex index buffer.
     * @param targetTriangleRatio Target ratio of surviving triangles (0f..1f).
     * @param lockBoundaries When true, open boundary / seam vertices remain strictly fixed at their
     * original 3D positions and boundary loop edges are preserved, preventing cracks or seam
     * separation across adjacent modular character submeshes.
     */
    fun simplify(
        positions: FloatArray,
        indices: IntArray,
        targetTriangleRatio: Float,
        lockBoundaries: Boolean = false,
    ): Result {
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

        val isBoundaryVertex = BooleanArray(vertexCount)
        for ((edge, count) in edgeTriangleCount) {
            if (count == 1) {
                isBoundaryVertex[edge.first] = true
                isBoundaryVertex[edge.second] = true
            }
        }

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
            val isBoundaryA = isBoundaryVertex[a]
            val isBoundaryB = isBoundaryVertex[b]

            if (lockBoundaries && isBoundaryA && isBoundaryB) return

            val key = edgeKey(a, b)
            val combined = DoubleArray(10)
            addWeightedQuadric(combined, quadrics[a], 1.0)
            addWeightedQuadric(combined, quadrics[b], 1.0)

            val target = when {
                lockBoundaries && isBoundaryA -> Vec3d(
                    working[a * POSITION_COMPONENTS],
                    working[a * POSITION_COMPONENTS + 1],
                    working[a * POSITION_COMPONENTS + 2],
                )
                lockBoundaries && isBoundaryB -> Vec3d(
                    working[b * POSITION_COMPONENTS],
                    working[b * POSITION_COMPONENTS + 1],
                    working[b * POSITION_COMPONENTS + 2],
                )
                else -> solveOptimalPosition(combined) ?: midpoint(working, a, b)
            }

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

            val survivor = if (lockBoundaries && isBoundaryVertex[next.b] && !isBoundaryVertex[next.a]) next.b else next.a
            val collapsed = if (survivor == next.a) next.b else next.a

            if (createsFlippedTriangle(working, vertexTriangles, triangleVertices, triangleAlive, survivor, collapsed, next.target)) continue

            working[survivor * POSITION_COMPONENTS] = next.target.x
            working[survivor * POSITION_COMPONENTS + 1] = next.target.y
            working[survivor * POSITION_COMPONENTS + 2] = next.target.z
            quadrics[survivor] = next.quadric

            val survivorTriangles = vertexTriangles[survivor]
            for (t in vertexTriangles[collapsed]) {
                if (!triangleAlive[t]) continue
                for (slot in 0 until VERTICES_PER_TRIANGLE) {
                    if (triangleVertices[t * VERTICES_PER_TRIANGLE + slot] == collapsed) triangleVertices[t * VERTICES_PER_TRIANGLE + slot] = survivor
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
            vertexTriangles[collapsed].clear()
            vertexAlive[collapsed] = false
            vertexOwner[collapsed] = survivor

            val neighbors = mutableSetOf<Int>()
            for (t in survivorTriangles) {
                if (!triangleAlive[t]) continue
                for (slot in 0 until VERTICES_PER_TRIANGLE) {
                    val v = triangleVertices[t * VERTICES_PER_TRIANGLE + slot]
                    if (v != survivor) neighbors += v
                }
            }
            for (neighbor in neighbors) {
                edgeCandidates.remove(edgeKey(neighbor, collapsed))
                if (vertexAlive[neighbor]) refreshEdge(survivor, neighbor)
            }
        }

        return buildResult(working, vertexCount, vertexAlive, vertexOwner, triangleAlive, triangleVertices)
    }

    private data class EdgeCandidate(
        val a: Int,
        val b: Int,
        val quadric: DoubleArray,
        val target: Vec3d,
        val cost: Double,
    )

    private class CandidateHeap {
        private val list = ArrayList<EdgeCandidate>()
        private var supersededCount = 0

        fun push(candidate: EdgeCandidate) {
            list += candidate
            siftUp(list.lastIndex)
        }

        fun pop(liveMap: Map<Pair<Int, Int>, EdgeCandidate>, vertexAlive: BooleanArray): EdgeCandidate? {
            while (list.isNotEmpty()) {
                val candidate = list[0]
                val last = list.removeAt(list.lastIndex)
                if (list.isNotEmpty()) {
                    list[0] = last
                    siftDown(0)
                }
                if (!vertexAlive[candidate.a] || !vertexAlive[candidate.b]) continue
                val current = liveMap[edgeKey(candidate.a, candidate.b)] ?: continue
                if (current !== candidate) {
                    supersededCount += 1
                    continue
                }
                if (supersededCount > list.size * COMPACT_RATIO) compact(liveMap, vertexAlive)
                return candidate
            }
            return null
        }

        private fun compact(liveMap: Map<Pair<Int, Int>, EdgeCandidate>, vertexAlive: BooleanArray) {
            val valid = list.filter {
                vertexAlive[it.a] && vertexAlive[it.b] && liveMap[edgeKey(it.a, it.b)] === it
            }
            list.clear()
            list.addAll(valid)
            supersededCount = 0
            for (i in (list.size / 2 - 1) downTo 0) {
                siftDown(i)
            }
        }

        private fun siftUp(index: Int) {
            var curr = index
            while (curr > 0) {
                val parent = (curr - 1) / 2
                if (list[curr].cost < list[parent].cost) {
                    val tmp = list[curr]
                    list[curr] = list[parent]
                    list[parent] = tmp
                    curr = parent
                } else {
                    break
                }
            }
        }

        private fun siftDown(index: Int) {
            var curr = index
            val size = list.size
            while (true) {
                val left = 2 * curr + 1
                val right = 2 * curr + 2
                var smallest = curr
                if (left < size && list[left].cost < list[smallest].cost) smallest = left
                if (right < size && list[right].cost < list[smallest].cost) smallest = right
                if (smallest != curr) {
                    val tmp = list[curr]
                    list[curr] = list[smallest]
                    list[smallest] = tmp
                    curr = smallest
                } else {
                    break
                }
            }
        }
    }

    private fun edgeKey(a: Int, b: Int): Pair<Int, Int> = if (a < b) Pair(a, b) else Pair(b, a)

    private fun triangleEdges(i0: Int, i1: Int, i2: Int) = arrayOf(
        edgeKey(i0, i1),
        edgeKey(i1, i2),
        edgeKey(i2, i0),
    )

    private fun trianglePlane(p: DoubleArray, i0: Int, i1: Int, i2: Int): DoubleArray? {
        val v0 = Vec3d(p[i0 * POSITION_COMPONENTS], p[i0 * POSITION_COMPONENTS + 1], p[i0 * POSITION_COMPONENTS + 2])
        val v1 = Vec3d(p[i1 * POSITION_COMPONENTS], p[i1 * POSITION_COMPONENTS + 1], p[i1 * POSITION_COMPONENTS + 2])
        val v2 = Vec3d(p[i2 * POSITION_COMPONENTS], p[i2 * POSITION_COMPONENTS + 1], p[i2 * POSITION_COMPONENTS + 2])
        val a = v1 - v0
        val b = v2 - v0
        val n = a.cross(b)
        if (n.length3() < DEGENERATE_NORMAL_LENGTH) return null
        n.normalize()
        val d = -n.dot(v0)
        return doubleArrayOf(n.x, n.y, n.z, d)
    }

    private fun boundaryQuadric(p: DoubleArray, a: Int, b: Int): DoubleArray? {
        val va = Vec3d(p[a * POSITION_COMPONENTS], p[a * POSITION_COMPONENTS + 1], p[a * POSITION_COMPONENTS + 2])
        val vb = Vec3d(p[b * POSITION_COMPONENTS], p[b * POSITION_COMPONENTS + 1], p[b * POSITION_COMPONENTS + 2])
        val e = vb - va
        if (e.length3() < DEGENERATE_NORMAL_LENGTH) return null
        val ax = if (e.x < 0.0) -e.x else e.x
        val ay = if (e.y < 0.0) -e.y else e.y
        val helper = if (ax < ay) Vec3d(1.0, 0.0, 0.0) else Vec3d(0.0, 1.0, 0.0)
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

    private fun quadricError(q: DoubleArray, v: Vec3d): Double {
        val x = v.x
        val y = v.y
        val z = v.z
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x +
            q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y +
            q[7] * z * z + 2 * q[8] * z +
            q[9]
    }

    private fun solveOptimalPosition(q: DoubleArray): Vec3d? {
        val a00 = q[0]
        val a01 = q[1]
        val a02 = q[2]
        val a10 = q[1]
        val a11 = q[4]
        val a12 = q[5]
        val a20 = q[2]
        val a21 = q[5]
        val a22 = q[7]
        val bx = -q[3]
        val by = -q[6]
        val bz = -q[8]

        val det = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20)
        val absDet = if (det < 0.0) -det else det
        if (absDet < SINGULAR_DETERMINANT) return null

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
