/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.math

import kotlin.math.sqrt

/**
 * A mutable 3-component double-precision vector.
 *
 * Naming contract mirrors [Vec3f] exactly:
 * - **Bare imperative verb** ([set], [add], [sub], [scale], [lerp], [normalize]) **mutates `this`**
 *   and returns `this` so calls can be chained. Nothing is allocated.
 * - **`-ed` suffix** ([normalized]) and **operators** ([plus], [minus], [times]) are pure:
 *   they allocate a new [Vec3d] and leave the receiver untouched.
 * - **Products and queries** ([dot], [cross], [length3]) are pure by nature.
 */
data class Vec3d(var x: Double = 1.0, var y: Double = 1.0, var z: Double = 1.0) {
    fun set(x: Double, y: Double, z: Double): Vec3d {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun set(other: Vec3d): Vec3d {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        return this
    }

    fun add(other: Vec3d): Vec3d {
        this.x += other.x
        this.y += other.y
        this.z += other.z
        return this
    }

    fun sub(other: Vec3d): Vec3d {
        this.x -= other.x
        this.y -= other.y
        this.z -= other.z
        return this
    }

    fun scale(scalar: Double): Vec3d {
        this.x *= scalar
        this.y *= scalar
        this.z *= scalar
        return this
    }

    fun lerp(target: Vec3d, factor: Double): Vec3d {
        this.x += (target.x - this.x) * factor
        this.y += (target.y - this.y) * factor
        this.z += (target.z - this.z) * factor
        return this
    }

    /**
     * Length over x/y/z only: `sqrt(x * x + y * y + z * z)`.
     */
    fun length3(): Double = sqrt(x * x + y * y + z * z)

    /**
     * Squared distance between this point and [other].
     */
    fun squaredDistanceTo(other: Vec3d): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Scales this vector to unit length in place. A zero-length vector is left alone.
     */
    fun normalize(): Vec3d {
        val length = length3()
        if (length != 0.0) {
            val invLength = 1.0 / length
            x *= invLength
            y *= invLength
            z *= invLength
        }
        return this
    }

    /** Allocating counterpart of [normalize]: returns a new unit-length copy. */
    fun normalized(): Vec3d = Vec3d(x, y, z).normalize()

    fun dot(other: Vec3d): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3d): Vec3d = Vec3d(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )

    fun cross(other: Vec3d, out: Vec3d): Vec3d {
        val cx = y * other.z - z * other.y
        val cy = z * other.x - x * other.z
        val cz = x * other.y - y * other.x
        return out.set(cx, cy, cz)
    }

    operator fun minus(other: Vec3d): Vec3d = Vec3d(
        x - other.x,
        y - other.y,
        z - other.z,
    )

    operator fun plus(other: Vec3d): Vec3d = Vec3d(
        x + other.x,
        y + other.y,
        z + other.z,
    )

    operator fun times(scalar: Double): Vec3d = Vec3d(x * scalar, y * scalar, z * scalar)

    companion object {
        val ZERO: Vec3d get() = Vec3d(0.0, 0.0, 0.0)
        val ONE: Vec3d get() = Vec3d(1.0, 1.0, 1.0)

        val UP: Vec3d get() = Vec3d(0.0, 1.0, 0.0)
        val DOWN: Vec3d get() = Vec3d(0.0, -1.0, 0.0)
        val RIGHT: Vec3d get() = Vec3d(1.0, 0.0, 0.0)
        val LEFT: Vec3d get() = Vec3d(-1.0, 0.0, 0.0)
        val FORWARD: Vec3d get() = Vec3d(0.0, 0.0, -1.0)
        val BACK: Vec3d get() = Vec3d(0.0, 0.0, 1.0)
    }
}

fun io.github.awakelab.awake.core.math.Vec3f.toVec3d(): Vec3d =
    Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3d.toVec3(): io.github.awakelab.awake.core.math.Vec3f =
    Vec3f(x.toFloat(), y.toFloat(), z.toFloat())
