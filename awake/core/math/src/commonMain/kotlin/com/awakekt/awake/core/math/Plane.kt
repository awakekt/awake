/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.sqrt

/**
 * An infinite plane, stored as a unit [normal] and the signed [distance] from the origin along
 * it (`dot(normal, point) + distance == 0` on the surface).
 *
 * The constraint surface a plane-locked gizmo drag, a ground snap, or a frustum side needs.
 * [normal] is normalized on construction for the same reason [Ray]'s direction is: an
 * unnormalized normal silently scales every distance this plane reports.
 *
 * @property normal Unit normal vector perpendicular to the plane surface.
 * @property distance Signed perpendicular distance from the world origin to the plane along [normal].
 */
data class Plane(val normal: com.awakekt.awake.core.math.Vec3f, val distance: Float) {

    init {
        val length = sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z)
        require(length > EPSILON) { "A plane needs a normal, got $normal." }
        normal.x /= length
        normal.y /= length
        normal.z /= length
    }

    /** Positive in front of the plane (the side [normal] points to), negative behind, zero on it. */
    fun signedDistanceTo(point: com.awakekt.awake.core.math.Vec3f): Float =
        normal.x * point.x + normal.y * point.y + normal.z * point.z + distance

    /**
     * Projects [point] orthogonally onto the surface of this plane.
     *
     * @param point The world-space point to project.
     * @return The closest point on the plane to [point].
     */
    fun project(point: com.awakekt.awake.core.math.Vec3f): com.awakekt.awake.core.math.Vec3f {
        val signed = signedDistanceTo(point)
        return Vec3f(
            point.x - normal.x * signed,
            point.y - normal.y * signed,
            point.z - normal.z * signed,
        )
    }

    /** Factory methods for constructing [Plane] instances. */
    companion object {
        private const val EPSILON = 1e-6f

        /** The plane with [normal] passing through [point] -- how a caller with a surface and a
         * direction actually thinks about it, rather than solving for the origin distance. */
        fun through(
            point: com.awakekt.awake.core.math.Vec3f,
            normal: com.awakekt.awake.core.math.Vec3f,
        ): Plane {
            val length = sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z)
            require(length > EPSILON) { "A plane needs a normal, got $normal." }
            val unit = Vec3f(normal.x / length, normal.y / length, normal.z / length)
            return Plane(unit, -(unit.x * point.x + unit.y * point.y + unit.z * point.z))
        }
    }
}
