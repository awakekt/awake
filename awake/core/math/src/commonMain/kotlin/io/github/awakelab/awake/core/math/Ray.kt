/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A world-space ray: where picking, ground-snapping and line-of-sight queries all start.
 *
 * [direction] is normalized on construction, so every returned distance is in world units. A
 * caller building one from two points ([through]) or from an unprojected screen pixel
 * ([Mat4.inverse]) has an unnormalized direction, and a "distance" measured in unnormalized
 * direction-lengths is the kind of unit that quietly disagrees with everything else.
 */
data class Ray(
    val origin: io.github.awakelab.awake.core.math.Vec3f,
    val direction: io.github.awakelab.awake.core.math.Vec3f,
) {

    init {
        val length =
            sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        require(length > EPSILON) { "A ray needs a direction, got $direction." }
        direction.x /= length
        direction.y /= length
        direction.z /= length
    }

    fun pointAt(distance: Float): io.github.awakelab.awake.core.math.Vec3f = Vec3f(
        origin.x + direction.x * distance,
        origin.y + direction.y * distance,
        origin.z + direction.z * distance,
    )

    /**
     * Distance to the near intersection with [sphere] of [radius], or `null` when the ray misses.
     *
     * A ray starting inside the sphere returns `0`: it is already touching, and reporting the
     * exit point instead would make a click inside an object select whatever is behind it.
     */
    fun intersectSphere(
        center: io.github.awakelab.awake.core.math.Vec3f,
        radius: Float,
    ): Float? {
        val toCentreX = center.x - origin.x
        val toCentreY = center.y - origin.y
        val toCentreZ = center.z - origin.z
        val projection = toCentreX * direction.x + toCentreY * direction.y + toCentreZ * direction.z
        val distanceSquared = toCentreX * toCentreX + toCentreY * toCentreY + toCentreZ * toCentreZ
        val radiusSquared = radius * radius
        val perpendicularSquared = distanceSquared - projection * projection
        return when {
            distanceSquared <= radiusSquared -> 0f
            projection < 0f -> null
            perpendicularSquared > radiusSquared -> null
            else -> projection - sqrt(radiusSquared - perpendicularSquared)
        }
    }

    /**
     * Distance to the near intersection with [box], or `null` when the ray misses. Slab method:
     * clip the ray against each axis pair and see whether an interval survives.
     */
    fun intersectAabb(box: Aabb): Float? {
        var near = 0f
        var far = Float.MAX_VALUE
        var hit = true

        fun clipSlab(rayOrigin: Float, rayDirection: Float, slabMin: Float, slabMax: Float) {
            if (!hit) return
            if (abs(rayDirection) < EPSILON) {
                // Parallel to this slab: only a miss if the origin already sits outside it.
                if (rayOrigin < slabMin || rayOrigin > slabMax) hit = false
                return
            }
            val inverse = 1f / rayDirection
            val first = (slabMin - rayOrigin) * inverse
            val second = (slabMax - rayOrigin) * inverse
            near = max(near, min(first, second))
            far = min(far, max(first, second))
            if (near > far) hit = false
        }

        clipSlab(origin.x, direction.x, box.min.x, box.max.x)
        clipSlab(origin.y, direction.y, box.min.y, box.max.y)
        clipSlab(origin.z, direction.z, box.min.z, box.max.z)
        return near.takeIf { hit }
    }

    /** Distance to [plane], or `null` when the ray is parallel to it or points away. */
    fun intersectPlane(plane: Plane): Float? {
        val denominator =
            plane.normal.x * direction.x + plane.normal.y * direction.y + plane.normal.z * direction.z
        if (abs(denominator) < EPSILON) return null
        return (-plane.signedDistanceTo(origin) / denominator).takeIf { it >= 0f }
    }

    /**
     * Intersects this ray with a horizontal ground plane at [groundY].
     *
     * If the ray intersects the plane in front of its origin, returns the 3D world intersection point.
     * If the ray is parallel to the ground or points away, returns a point projected along the ray
     * at [fallbackDistance].
     *
     * @param groundY The vertical Y coordinate of the horizontal ground plane (defaults to 0.0).
     * @param fallbackDistance Fallback distance along the ray if no forward intersection exists.
     * @return World coordinates of the hit point.
     */
    fun intersectGroundPlane(
        groundY: Float = 0f,
        fallbackDistance: Float = 10f,
    ): Vec3f {
        if (abs(direction.y) > EPSILON) {
            val t = (groundY - origin.y) / direction.y
            if (t > 0f) {
                return pointAt(t)
            }
        }
        return pointAt(fallbackDistance)
    }

    companion object {
        private const val EPSILON = 1e-6f

        /** The ray from [from] toward [to] -- the shape most callers actually have (two points),
         * rather than a point and a direction they would otherwise subtract by hand. */
        fun through(
            from: io.github.awakelab.awake.core.math.Vec3f,
            to: io.github.awakelab.awake.core.math.Vec3f,
        ): Ray =
            Ray(Vec3f(from.x, from.y, from.z), Vec3f(to.x - from.x, to.y - from.y, to.z - from.z))
    }
}
