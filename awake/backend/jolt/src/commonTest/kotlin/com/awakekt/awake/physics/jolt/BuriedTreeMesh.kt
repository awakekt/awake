/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.jolt

import com.awakekt.awake.physics.MeshShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A tree-like static mesh standing at the origin on ground that rises [slope] per metre along x: a
 * square trunk that goes 1.5 m into the ground, and [roots] closed roots running from up the trunk
 * out and down into the slope, partly buried.
 *
 * Every face is one-sided and faces outward, as an imported model's would. The roots are what a
 * character walking around the tree presses into, with the terrain beside and under them.
 */
internal fun buriedTreeMesh(slope: Float, roots: Int = 7): MeshShape {
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Int>()

    // Two triangles, a-b-c and a-c-d, counter-clockwise seen from the side they face.
    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        val base = vertices.size / 3
        listOf(a, b, c, d).forEach { vertices += it.toList() }
        indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
    }

    val half = 0.35f
    val bottom = -1.5f
    val top = 4f
    quad(p(-half, bottom, half), p(half, bottom, half), p(half, top, half), p(-half, top, half))
    quad(p(half, bottom, -half), p(-half, bottom, -half), p(-half, top, -half), p(half, top, -half))
    quad(p(half, bottom, half), p(half, bottom, -half), p(half, top, -half), p(half, top, half))
    quad(p(-half, bottom, -half), p(-half, bottom, half), p(-half, top, half), p(-half, top, -half))

    for (k in 0 until roots) {
        val angle = k * 2 * PI / roots + 0.3
        val cx = cos(angle).toFloat()
        val cz = sin(angle).toFloat()
        // Half the root's width, across it.
        val wx = -cz * 0.25f
        val wz = cx * 0.25f
        val ix = cx * half
        val iz = cz * half
        val ox = cx * 3f
        val oz = cz * 3f
        // From 0.8 m up the trunk to 0.7 m under the ground, 3 m out.
        val iy = 0.8f + ix * slope
        val oy = ox * slope - 0.7f
        // A triangular cross-section: a ridge on top and two bottom edges.
        val ridge0 = p(ix, iy + 0.25f, iz)
        val ridge1 = p(ox, oy + 0.25f, oz)
        val left0 = p(ix - wx, iy - 0.25f, iz - wz)
        val left1 = p(ox - wx, oy - 0.25f, oz - wz)
        val right0 = p(ix + wx, iy - 0.25f, iz + wz)
        val right1 = p(ox + wx, oy - 0.25f, oz + wz)
        quad(left0, ridge0, ridge1, left1)
        quad(ridge0, right0, right1, ridge1)
        quad(right0, left0, left1, right1)
        // The ends, each a triangle: d repeats c.
        quad(left0, right0, ridge0, ridge0)
        quad(left1, ridge1, right1, right1)
    }
    return MeshShape(vertices.toFloatArray(), indices.toIntArray())
}

private fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
