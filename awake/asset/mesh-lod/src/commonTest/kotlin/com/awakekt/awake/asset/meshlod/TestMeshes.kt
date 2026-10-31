/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A mesh with the vertex streams a glTF primitive carries. */
class TestMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val joints: IntArray? = null,
    val weights: FloatArray? = null,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

object TestMeshes {
    /**
     * A UV sphere whose seam column is duplicated (u = 0 and u = 1 at the same positions) and whose poles
     * have one vertex per segment, the way a DCC tool exports one.
     */
    fun uvSphere(segments: Int, rings: Int, radius: Float = 2f): TestMesh {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        for (ring in 0..rings) {
            val v = ring.toFloat() / rings
            val theta = v * PI
            for (segment in 0..segments) {
                val u = segment.toFloat() / segments
                val phi = u * 2 * PI
                val x = (sin(theta) * cos(phi)).toFloat()
                val y = cos(theta).toFloat()
                val z = (sin(theta) * sin(phi)).toFloat()
                positions += listOf(x * radius, y * radius, z * radius)
                normals += listOf(x, y, z)
                uvs += listOf(u, v)
            }
        }
        // Exact poles and seam: the same floats at every duplicate.
        val stride = segments + 1
        for (ring in 0..rings) {
            val first = ring * stride
            val last = first + segments
            for (axis in 0 until 3) positions[last * 3 + axis] = positions[first * 3 + axis]
        }
        for (pole in listOf(0, rings)) {
            for (segment in 0..segments) {
                val i = pole * stride + segment
                positions[i * 3] = 0f
                positions[i * 3 + 2] = 0f
            }
        }
        val indices = ArrayList<Int>()
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val a = ring * stride + segment
                val b = a + stride
                if (ring != 0) indices += listOf(a, a + 1, b)
                if (ring != rings - 1) indices += listOf(a + 1, b + 1, b)
            }
        }
        return TestMesh(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
    }

    /**
     * A capped-open tube along y, bound to joint 0 at the bottom and joint 1 at the top with a linear blend,
     * like a limb.
     */
    fun skinnedTube(segments: Int, rings: Int, height: Float = 4f): TestMesh {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val joints = ArrayList<Int>()
        val weights = ArrayList<Float>()
        for (ring in 0..rings) {
            val t = ring.toFloat() / rings
            for (segment in 0 until segments) {
                val phi = segment.toFloat() / segments * 2 * PI
                val x = cos(phi).toFloat()
                val z = sin(phi).toFloat()
                positions += listOf(x, t * height, z)
                normals += listOf(x, 0f, z)
                uvs += listOf(segment.toFloat() / segments, t)
                joints += listOf(0, 1, 0, 0)
                weights += listOf(1f - t, t, 0f, 0f)
            }
        }
        val indices = ArrayList<Int>()
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val a = ring * segments + segment
                val b = ring * segments + (segment + 1) % segments
                indices += listOf(a, b, a + segments, b, b + segments, a + segments)
            }
        }
        return TestMesh(
            positions.toFloatArray(),
            normals.toFloatArray(),
            uvs.toFloatArray(),
            indices.toIntArray(),
            joints.toIntArray(),
            weights.toFloatArray(),
        )
    }
}
