/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * A mutable 3-component integer vector for discrete grid, chunk, or voxel coordinates.
 *
 * Naming contract mirrors [Vec3f] where applicable:
 * - **Bare imperative verb** ([set], [add], [sub], [scale]) **mutates `this`** and returns `this`.
 * - **Operators** ([plus], [minus], [times]) allocate a new [Vec3i].
 * - **Queries** ([dot], [squaredDistanceTo], [manhattanDistanceTo]) are pure.
 *
 * Note: Integer vectors intentionally omit floating-point normalization and continuous lengths.
 *
 * @property x The X component of this vector.
 * @property y The Y component of this vector.
 * @property z The Z component of this vector.
 */
data class Vec3i(var x: Int = 0, var y: Int = 0, var z: Int = 0) {
    /**
     * Sets this vector's coordinates in place.
     *
     * @param x The new X component.
     * @param y The new Y component.
     * @param z The new Z component.
     * @return This vector for chaining without allocation.
     */
    fun set(x: Int, y: Int, z: Int): Vec3i {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    /**
     * Copies coordinates from [other] into this vector in place.
     *
     * @param other Source vector to copy from.
     * @return This vector for chaining without allocation.
     */
    fun set(other: Vec3i): Vec3i {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    /**
     * Adds [other] into this vector in place.
     *
     * @param other The vector to add.
     * @return This vector for chaining without allocation.
     */
    fun add(other: Vec3i): Vec3i {
        this.x += other.x
        this.y += other.y
        this.z += other.z
        return this
    }

    /**
     * Subtracts [other] from this vector in place.
     *
     * @param other The vector to subtract.
     * @return This vector for chaining without allocation.
     */
    fun sub(other: Vec3i): Vec3i {
        this.x -= other.x
        this.y -= other.y
        this.z -= other.z
        return this
    }

    /**
     * Multiplies all components of this vector by [scalar] in place.
     *
     * @param scalar Multiplier factor.
     * @return This vector for chaining without allocation.
     */
    fun scale(scalar: Int): Vec3i {
        this.x *= scalar
        this.y *= scalar
        this.z *= scalar
        return this
    }

    /**
     * Squared distance between this coordinate and [other].
     * Uses [Long] arithmetic to prevent integer overflow.
     */
    fun squaredDistanceTo(other: Vec3i): Long {
        val dx = (x - other.x).toLong()
        val dy = (y - other.y).toLong()
        val dz = (z - other.z).toLong()
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Manhattan distance (L1 norm) between this coordinate and [other].
     */
    fun manhattanDistanceTo(other: Vec3i): Int {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        val ax = if (dx < 0) -dx else dx
        val ay = if (dy < 0) -dy else dy
        val az = if (dz < 0) -dz else dz
        return ax + ay + az
    }

    /**
     * Dot product computed with [Long] accumulation to avoid overflow.
     */
    fun dot(other: Vec3i): Long = x.toLong() * other.x + y.toLong() * other.y + z.toLong() * other.z

    /**
     * Subtracts [other] from this vector and returns the result as a new vector.
     *
     * @param other Vector to subtract.
     * @return A new [Vec3i] with the difference.
     */
    operator fun minus(other: Vec3i): Vec3i = Vec3i(
        x - other.x,
        y - other.y,
        z - other.z,
    )

    /**
     * Adds [other] to this vector and returns the result as a new vector.
     *
     * @param other Vector to add.
     * @return A new [Vec3i] with the sum.
     */
    operator fun plus(other: Vec3i): Vec3i = Vec3i(
        x + other.x,
        y + other.y,
        z + other.z,
    )

    /**
     * Multiplies this vector by [scalar] and returns the result as a new vector.
     *
     * @param scalar Multiplier factor.
     * @return A new [Vec3i] scaled by [scalar].
     */
    operator fun times(scalar: Int): Vec3i = Vec3i(x * scalar, y * scalar, z * scalar)

    /** Factory properties providing standard integer directional and unit vectors. */
    companion object {
        /** Returns a fresh zero vector `(0, 0, 0)`. */
        val ZERO: Vec3i get() = Vec3i(0, 0, 0)

        /** Returns a fresh unit vector `(1, 1, 1)`. */
        val ONE: Vec3i get() = Vec3i(1, 1, 1)

        /** Returns a fresh unit vector pointing upward `(0, 1, 0)`. */
        val UP: Vec3i get() = Vec3i(0, 1, 0)

        /** Returns a fresh unit vector pointing downward `(0, -1, 0)`. */
        val DOWN: Vec3i get() = Vec3i(0, -1, 0)

        /** Returns a fresh unit vector pointing right `(1, 0, 0)`. */
        val RIGHT: Vec3i get() = Vec3i(1, 0, 0)

        /** Returns a fresh unit vector pointing left `(-1, 0, 0)`. */
        val LEFT: Vec3i get() = Vec3i(-1, 0, 0)

        /** Returns a fresh unit vector pointing forward `(0, 0, -1)`. */
        val FORWARD: Vec3i get() = Vec3i(0, 0, -1)

        /** Returns a fresh unit vector pointing backward `(0, 0, 1)`. */
        val BACK: Vec3i get() = Vec3i(0, 0, 1)
    }
}

/**
 * Truncates this single-precision vector to an integer [Vec3i].
 *
 * @return A new [Vec3i] containing truncated integer coordinates.
 */
fun com.awakekt.awake.core.math.Vec3f.toVec3i(): Vec3i =
    Vec3i(x.toInt(), y.toInt(), z.toInt())

/**
 * Truncates this double-precision vector to an integer [Vec3i].
 *
 * @return A new [Vec3i] containing truncated integer coordinates.
 */
fun Vec3d.toVec3i(): Vec3i = Vec3i(x.toInt(), y.toInt(), z.toInt())

/**
 * Converts this integer vector to a single-precision [Vec3f].
 *
 * @return A new [Vec3f] containing floating-point coordinates.
 */
fun Vec3i.toVec3(): com.awakekt.awake.core.math.Vec3f =
    Vec3f(x.toFloat(), y.toFloat(), z.toFloat())

/**
 * Converts this integer vector to a double-precision [Vec3d].
 *
 * @return A new [Vec3d] containing double-precision coordinates.
 */
fun Vec3i.toVec3d(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())
