/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

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
 *
 * @property x The X component of this vector.
 * @property y The Y component of this vector.
 * @property z The Z component of this vector.
 */
data class Vec3d(var x: Double = 1.0, var y: Double = 1.0, var z: Double = 1.0) {
    /**
     * Sets this vector's coordinates in place.
     *
     * @param x The new X component.
     * @param y The new Y component.
     * @param z The new Z component.
     * @return This vector for chaining without allocation.
     */
    fun set(x: Double, y: Double, z: Double): Vec3d {
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
    fun set(other: Vec3d): Vec3d {
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
    fun add(other: Vec3d): Vec3d {
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
    fun sub(other: Vec3d): Vec3d {
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
    fun scale(scalar: Double): Vec3d {
        this.x *= scalar
        this.y *= scalar
        this.z *= scalar
        return this
    }

    /**
     * Linearly interpolates this vector toward [target] by [factor] in place.
     *
     * @param target The target vector to interpolate toward.
     * @param factor Interpolation progress between 0.0 (this) and 1.0 (target).
     * @return This vector for chaining without allocation.
     */
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

    /**
     * Computes the scalar dot product of this vector and [other].
     *
     * @param other The vector to compute dot product with.
     * @return The scalar dot product.
     */
    fun dot(other: Vec3d): Double = x * other.x + y * other.y + z * other.z

    /**
     * Computes the vector cross product of this vector and [other], returning a new vector.
     *
     * @param other The right vector operand.
     * @return A new [Vec3d] orthogonal to both vectors.
     */
    fun cross(other: Vec3d): Vec3d = Vec3d(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x,
    )

    /**
     * Computes the vector cross product of this vector and [other], writing the result into [out].
     *
     * @param other The right vector operand.
     * @param out The destination vector to store the result without allocation.
     * @return [out] containing the computed cross product.
     */
    fun cross(other: Vec3d, out: Vec3d): Vec3d {
        val cx = y * other.z - z * other.y
        val cy = z * other.x - x * other.z
        val cz = x * other.y - y * other.x
        return out.set(cx, cy, cz)
    }

    /**
     * Subtracts [other] from this vector and returns the result as a new vector.
     *
     * @param other Vector to subtract.
     * @return A new [Vec3d] with the difference.
     */
    operator fun minus(other: Vec3d): Vec3d = Vec3d(
        x - other.x,
        y - other.y,
        z - other.z,
    )

    /**
     * Adds [other] to this vector and returns the result as a new vector.
     *
     * @param other Vector to add.
     * @return A new [Vec3d] with the sum.
     */
    operator fun plus(other: Vec3d): Vec3d = Vec3d(
        x + other.x,
        y + other.y,
        z + other.z,
    )

    /**
     * Multiplies this vector by [scalar] and returns the result as a new vector.
     *
     * @param scalar Multiplier factor.
     * @return A new [Vec3d] scaled by [scalar].
     */
    operator fun times(scalar: Double): Vec3d = Vec3d(x * scalar, y * scalar, z * scalar)

    /** Factory properties providing standard directional and unit vectors. */
    companion object {
        /** Returns a fresh zero vector `(0, 0, 0)`. */
        val ZERO: Vec3d get() = Vec3d(0.0, 0.0, 0.0)

        /** Returns a fresh unit vector `(1, 1, 1)`. */
        val ONE: Vec3d get() = Vec3d(1.0, 1.0, 1.0)

        /** Returns a fresh unit vector pointing upward `(0, 1, 0)`. */
        val UP: Vec3d get() = Vec3d(0.0, 1.0, 0.0)

        /** Returns a fresh unit vector pointing downward `(0, -1, 0)`. */
        val DOWN: Vec3d get() = Vec3d(0.0, -1.0, 0.0)

        /** Returns a fresh unit vector pointing right `(1, 0, 0)`. */
        val RIGHT: Vec3d get() = Vec3d(1.0, 0.0, 0.0)

        /** Returns a fresh unit vector pointing left `(-1, 0, 0)`. */
        val LEFT: Vec3d get() = Vec3d(-1.0, 0.0, 0.0)

        /** Returns a fresh unit vector pointing forward `(0, 0, -1)`. */
        val FORWARD: Vec3d get() = Vec3d(0.0, 0.0, -1.0)

        /** Returns a fresh unit vector pointing backward `(0, 0, 1)`. */
        val BACK: Vec3d get() = Vec3d(0.0, 0.0, 1.0)
    }
}

/**
 * Converts this single-precision vector to a double-precision [Vec3d].
 *
 * @return A new [Vec3d] containing double-precision converted components.
 */
fun com.awakekt.awake.core.math.Vec3f.toVec3d(): Vec3d =
    Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * Converts this double-precision vector to a single-precision [Vec3f].
 *
 * @return A new [Vec3f] containing single-precision converted components.
 */
fun Vec3d.toVec3(): com.awakekt.awake.core.math.Vec3f =
    Vec3f(x.toFloat(), y.toFloat(), z.toFloat())
