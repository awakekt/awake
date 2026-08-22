// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.math

/**
 * A mutable 3-component integer vector for discrete grid, chunk, or voxel coordinates.
 *
 * Naming contract mirrors [Vec3f] where applicable:
 * - **Bare imperative verb** ([set], [add], [sub], [scale]) **mutates `this`** and returns `this`.
 * - **Operators** ([plus], [minus], [times]) allocate a new [Vec3i].
 * - **Queries** ([dot], [squaredDistanceTo], [manhattanDistanceTo]) are pure.
 *
 * Note: Integer vectors intentionally omit floating-point normalization and continuous lengths.
 */
data class Vec3i(var x: Int = 0, var y: Int = 0, var z: Int = 0) {
    fun set(x: Int, y: Int, z: Int): Vec3i {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun set(other: Vec3i): Vec3i {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    fun add(other: Vec3i): Vec3i {
        this.x += other.x
        this.y += other.y
        this.z += other.z
        return this
    }

    fun sub(other: Vec3i): Vec3i {
        this.x -= other.x
        this.y -= other.y
        this.z -= other.z
        return this
    }

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

    operator fun minus(other: Vec3i): Vec3i = Vec3i(
        x - other.x,
        y - other.y,
        z - other.z,
    )

    operator fun plus(other: Vec3i): Vec3i = Vec3i(
        x + other.x,
        y + other.y,
        z + other.z,
    )

    operator fun times(scalar: Int): Vec3i = Vec3i(x * scalar, y * scalar, z * scalar)

    companion object {
        val ZERO: Vec3i get() = Vec3i(0, 0, 0)
        val ONE: Vec3i get() = Vec3i(1, 1, 1)

        val UP: Vec3i get() = Vec3i(0, 1, 0)
        val DOWN: Vec3i get() = Vec3i(0, -1, 0)
        val RIGHT: Vec3i get() = Vec3i(1, 0, 0)
        val LEFT: Vec3i get() = Vec3i(-1, 0, 0)
        val FORWARD: Vec3i get() = Vec3i(0, 0, -1)
        val BACK: Vec3i get() = Vec3i(0, 0, 1)
    }
}

fun io.github.ronjunevaldoz.awake.core.math.Vec3f.toVec3i(): Vec3i =
    Vec3i(x.toInt(), y.toInt(), z.toInt())

fun Vec3d.toVec3i(): Vec3i = Vec3i(x.toInt(), y.toInt(), z.toInt())
fun Vec3i.toVec3(): io.github.ronjunevaldoz.awake.core.math.Vec3f =
    Vec3f(x.toFloat(), y.toFloat(), z.toFloat())

fun Vec3i.toVec3d(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())
