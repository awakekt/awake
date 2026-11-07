/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.trail

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Plane
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.intersects

/**
 * Whether a trail's visible pairs can be seen: the box around them, carried into the world by its
 * placement, against a view's frustum planes. Holds its scratch, so a test allocates nothing.
 */
internal class TrailCulling {
    private val localMin = Vec3f(0f, 0f, 0f)
    private val localMax = Vec3f(0f, 0f, 0f)

    /**
     * Whether [strip]'s visible pairs, placed by [placement], are at least partly inside [planes].
     * False when fewer than 2 pairs are visible; true for empty [planes], which cull nothing.
     */
    fun isVisible(strip: TrailStrip, placement: Mat4, planes: List<Plane>): Boolean {
        if (!strip.visibleBounds(localMin, localMax)) return false
        if (planes.isEmpty()) return true
        val m = placement.data
        // A box carried by a matrix: the centre moves with it, and the half-size along each world
        // axis is the sum of the absolute matrix terms times the local half-sizes (Arvo's method).
        val cx = (localMin.x + localMax.x) * HALF
        val cy = (localMin.y + localMax.y) * HALF
        val cz = (localMin.z + localMax.z) * HALF
        val hx = (localMax.x - localMin.x) * HALF
        val hy = (localMax.y - localMin.y) * HALF
        val hz = (localMax.z - localMin.z) * HALF
        val wx = m[0] * cx + m[4] * cy + m[8] * cz + m[12]
        val wy = m[1] * cx + m[5] * cy + m[9] * cz + m[13]
        val wz = m[2] * cx + m[6] * cy + m[10] * cz + m[14]
        val ex = abs(m[0]) * hx + abs(m[4]) * hy + abs(m[8]) * hz
        val ey = abs(m[1]) * hx + abs(m[5]) * hy + abs(m[9]) * hz
        val ez = abs(m[2]) * hx + abs(m[6]) * hy + abs(m[10]) * hz
        return planes.intersects(wx - ex, wy - ey, wz - ez, wx + ex, wy + ey, wz + ez)
    }

    private fun abs(value: Float): Float = if (value < 0f) -value else value

    private companion object {
        const val HALF = 0.5f
    }
}
