/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttributeMeshSimplifierTest {
    private val sphere = TestMeshes.uvSphere(segments = 48, rings = 24)

    private fun simplify(mesh: TestMesh, ratio: Float, maxError: Float = Float.MAX_VALUE) = AttributeMeshSimplifier.simplify(
        mesh.positions,
        mesh.indices,
        SimplifyOptions(targetRatio = ratio, maxError = maxError),
        attributes = listOf(MeshAttribute(mesh.normals, 3, 0.5f), MeshAttribute(mesh.uvs, 2, 1f)),
        skin = mesh.joints?.let { SkinInfluences(it, mesh.weights!!) },
    )

    @Test
    fun eachLevelLandsWithinToleranceOfItsTargetRatio() {
        for (ratio in listOf(0.5f, 0.25f, 0.1f)) {
            val result = simplify(sphere, ratio)
            val target = (sphere.triangleCount * ratio).roundToInt()
            val tolerance = sphere.triangleCount * TOLERANCE
            assertTrue(
                abs(result.triangleCount - target) <= tolerance,
                "ratio $ratio kept ${result.triangleCount} of ${sphere.triangleCount} triangles; target $target ± $tolerance",
            )
        }
    }

    @Test
    fun theUvSeamNeverTearsOrIsBridged() {
        val result = simplify(sphere, 0.1f)
        val indices = result.indices
        for (t in 0 until result.triangleCount) {
            val us = (0 until 3).map { sphere.uvs[indices[t * 3 + it] * 2] }
            assertTrue(us.max() - us.min() < 0.5f, "triangle $t spans the seam: u = $us")
        }
        assertEquals(emptyList(), openEdges(sphere.positions, indices), "the closed sphere opened a hole")
    }

    @Test
    fun keptVerticesKeepTheirOwnAttributesAndSkinWeights() {
        val tube = TestMeshes.skinnedTube(segments = 24, rings = 32)
        val result = simplify(tube, 0.25f)
        val weights = tube.weights!!
        assertTrue(result.triangleCount < tube.triangleCount / 2, "the tube kept ${result.triangleCount} triangles")
        for (vertex in result.indices.toSet()) {
            val height = tube.positions[vertex * 3 + 1] / 4f
            // Each kept vertex is an original one: its blend still matches where it sits on the limb.
            assertEquals(1f - height, weights[vertex * 4], 1e-5f, "vertex $vertex weight for joint 0")
            assertEquals(height, weights[vertex * 4 + 1], 1e-5f, "vertex $vertex weight for joint 1")
            assertEquals(1f, (0 until 4).sumOf { weights[vertex * 4 + it].toDouble() }.toFloat(), 1e-5f)
        }
        val borderHeights = openEdges(tube.positions, result.indices).flatMap { listOf(it.first, it.second) }
            .map { tube.positions[it * 3 + 1] }.toSet()
        assertEquals(setOf(0f, 4f), borderHeights, "the tube's open ends moved or a new hole opened")
    }

    @Test
    fun aMaxErrorStopsBeforeTheTarget() {
        val loose = simplify(sphere, 0f)
        val tight = simplify(sphere, 0f, maxError = 0.01f)
        assertTrue(tight.triangleCount > loose.triangleCount, "tight ${tight.triangleCount}, loose ${loose.triangleCount}")
        assertTrue(tight.error <= 0.01f, "error ${tight.error}")
    }

    @Test
    fun lockedBordersStayWhereTheyAre() {
        val tube = TestMeshes.skinnedTube(segments = 16, rings = 16)
        val result = AttributeMeshSimplifier.simplify(tube.positions, tube.indices, SimplifyOptions(0.1f, lockBorders = true))
        val bottomAndTop = (0 until tube.vertexCount).filter { it < 16 || it >= 16 * 16 }.toSet()
        assertTrue(bottomAndTop.all { it in result.indices.toSet() }, "a border vertex was collapsed away")
    }

    /** Edges with only one triangle, welding vertices by position; each end is a vertex at that position. */
    private fun openEdges(positions: FloatArray, indices: IntArray): List<Pair<Int, Int>> {
        val points = HashMap<Triple<Float, Float, Float>, Int>()
        val pointOf = IntArray(positions.size / 3) {
            points.getOrPut(Triple(positions[it * 3], positions[it * 3 + 1], positions[it * 3 + 2])) { it }
        }
        val counts = HashMap<Pair<Int, Int>, Int>()
        for (t in 0 until indices.size / 3) {
            for (corner in 0 until 3) {
                val a = pointOf[indices[t * 3 + corner]]
                val b = pointOf[indices[t * 3 + (corner + 1) % 3]]
                if (a == b) continue
                val key = if (a < b) a to b else b to a
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.filterValues { it == 1 }.keys.toList()
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
