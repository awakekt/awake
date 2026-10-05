/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import com.awakekt.awake.core.math.Vec3f.Companion.FORWARD
import com.awakekt.awake.core.math.Vec3f.Companion.RIGHT
import com.awakekt.awake.core.math.Vec3f.Companion.UP
import com.awakekt.awake.core.math2d.Vec2
import kotlin.math.atan2
import kotlin.math.sqrt

/** Typealias for single-precision 3-component vector [Vec3f]. */
typealias Vec3 = Vec3f

/**
 * A mutable 3-component vector.
 *
 * Naming contract -- every member here follows it, and so should anything added later:
 * - **Bare imperative verb** ([set], [add], [sub], [scale], [lerp], [normalize]) **mutates
 *   `this`** and returns `this` so calls can be chained. Nothing is allocated.
 * - **`-ed` suffix** ([normalized]) and the **operators** ([plus], [minus], [times]) are
 *   pure: they allocate a new [Vec3f] and leave the receiver untouched.
 * - **Products and queries** ([dot], [cross], [length3]) are pure by nature -- they answer a
 *   question about two vectors rather than transforming one.
 *
 * The mutating forms exist so per-frame systems can do vector math without allocating; the
 * pure forms exist so expression-style code reads naturally. Picking the wrong one is a
 * silent bug in one direction (`v.normalized()` whose result is dropped does nothing), so
 * prefer the mutating form inside `System.update` and the pure form everywhere else.
 *
 * @property x The X component of this vector.
 * @property y The Y component of this vector.
 * @property z The Z component of this vector.
 */
data class Vec3f(var x: Float = 1f, var y: Float = 1f, var z: Float = 1f) {
    /**
     * Sets this vector's coordinates in place.
     *
     * @param x The new X component.
     * @param y The new Y component.
     * @param z The new Z component.
     * @return This vector for chaining without allocation.
     */
    fun set(x: Float, y: Float, z: Float): Vec3f {
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
    fun set(other: Vec3f): Vec3f {
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
    fun add(other: Vec3f): Vec3f {
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
    fun sub(other: Vec3f): Vec3f {
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
    fun scale(scalar: Float): Vec3f {
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
    fun lerp(
        target: Vec3f,
        factor: Float,
    ): Vec3f {
        this.x += (target.x - this.x) * factor
        this.y += (target.y - this.y) * factor
        this.z += (target.z - this.z) * factor
        return this
    }

    /**
     * Length over x/y/z only: `sqrt(x * x + y * y + z * z)`.
     */
    fun length3(): Float = sqrt(x * x + y * y + z * z)

    /** Distance between this point and [other]. */
    fun distanceTo(other: Vec3f): Float = sqrt(squaredDistanceTo(other))

    /** Squared distance between this point and [other] on the XZ horizontal plane. */
    fun horizontalSquaredDistanceTo(other: Vec3f): Float {
        val dx = x - other.x
        val dz = z - other.z
        return dx * dx + dz * dz
    }

    /** Distance between this point and [other] on the XZ horizontal plane. */
    fun horizontalDistanceTo(other: Vec3f): Float = sqrt(horizontalSquaredDistanceTo(other))

    /**
     * Moves this point in-place towards [target] by at most [maxDistanceDelta] without overshoot.
     * Returns this instance for chaining.
     */
    fun moveTowards(target: Vec3f, maxDistanceDelta: Float): Vec3f {
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq == 0f || (maxDistanceDelta >= 0f && distSq <= maxDistanceDelta * maxDistanceDelta)) {
            return set(target)
        }
        val dist = sqrt(distSq)
        val step = maxDistanceDelta / dist
        x += dx * step
        y += dy * step
        z += dz * step
        return this
    }

    /**
     * Calculates the Y-axis yaw angle in radians to look from this position towards [target]
     * on the XZ ground plane.
     */
    fun yawTowards(target: Vec3f): Float = atan2(target.x - x, target.z - z)

    /** Squared distance between this point and [other] -- for comparing or ordering distances,
     * where the sqrt in `(a - b).length3()` changes nothing and that subtraction allocates a
     * throwaway `Vec3`. Not a distance: take `sqrt` if you need the real magnitude. */
    fun squaredDistanceTo(other: Vec3f): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Scales this vector to unit length **in place**. A zero-length vector is left alone
     * rather than producing NaN. Returns `this`, so `dir.normalize()` on its own line is a
     * complete, meaningful statement -- see [normalized] for the allocating variant.
     */
    fun normalize(): Vec3f {
        val length = length3()
        if (length != 0.0f) {
            val invLength = 1.0f / length
            x *= invLength
            y *= invLength
            z *= invLength
        }
        return this
    }

    /** Allocating counterpart of [normalize]: returns a new unit-length copy, leaving `this`
     * unchanged. Dropping the result of this call is always a no-op bug. */
    fun normalized(): Vec3f = Vec3f(x, y, z).normalize()

    /**
     * Computes the scalar dot product of this vector and [other].
     *
     * @param other The vector to compute dot product with.
     * @return The scalar dot product.
     */
    fun dot(other: Vec3f): Float =
        x * other.x + y * other.y + z * other.z

    /**
     * Computes the vector cross product of this vector and [other], returning a new vector.
     *
     * @param other The right vector operand.
     * @return A new [Vec3f] orthogonal to both vectors.
     */
    fun cross(other: Vec3f): Vec3f =
        Vec3f(
            x = y * other.z - z * other.y,
            y = z * other.x - x * other.z,
            z = x * other.y - y * other.x,
        )

    /**
     * Subtracts [other] from this vector and returns the result as a new vector.
     *
     * @param other Vector to subtract.
     * @return A new [Vec3f] with the difference.
     */
    operator fun minus(other: Vec3f): Vec3f =
        Vec3f(
            x - other.x,
            y - other.y,
            z - other.z,
        )

    /**
     * Adds [other] to this vector and returns the result as a new vector.
     *
     * @param other Vector to add.
     * @return A new [Vec3f] with the sum.
     */
    operator fun plus(other: Vec3f): Vec3f =
        Vec3f(
            x + other.x,
            y + other.y,
            z + other.z,
        )

    /**
     * Multiplies this vector by [scalar] and returns the result as a new vector.
     *
     * @param scalar Multiplier factor.
     * @return A new [Vec3f] scaled by [scalar].
     */
    operator fun times(scalar: Float): Vec3f =
        Vec3f(x * scalar, y * scalar, z * scalar)

    /**
     * Awake's world-space direction convention. Each is a `get()` property rather than a
     * stored `val`, so every read hands back a **fresh** vector: [Vec3f] is mutable, and a
     * shared instance could be aliased into a [set]/[add] chain and silently corrupt the
     * constant for every other caller in the process.
     *
     * Right-handed, Y-up, facing -Z -- the same convention `CameraSystem` builds its aim from
     * (yaw 0 looks along [FORWARD], +yaw turns toward [RIGHT], +pitch tilts toward [UP]).
     */
    companion object {
        /** Returns a fresh zero vector `(0, 0, 0)`. */
        val ZERO: Vec3f get() = Vec3f(0f, 0f, 0f)

        /** Returns a fresh unit vector `(1, 1, 1)`. */
        val ONE: Vec3f get() = Vec3f(1f, 1f, 1f)

        /** Returns a fresh unit vector pointing upward `(0, 1, 0)`. */
        val UP: Vec3f get() = Vec3f(0f, 1f, 0f)

        /** Returns a fresh unit vector pointing downward `(0, -1, 0)`. */
        val DOWN: Vec3f get() = Vec3f(0f, -1f, 0f)

        /** Returns a fresh unit vector pointing right `(1, 0, 0)`. */
        val RIGHT: Vec3f get() = Vec3f(1f, 0f, 0f)

        /** Returns a fresh unit vector pointing left `(-1, 0, 0)`. */
        val LEFT: Vec3f get() = Vec3f(-1f, 0f, 0f)

        /** Returns a fresh unit vector pointing forward `(0, 0, -1)`. */
        val FORWARD: Vec3f get() = Vec3f(0f, 0f, -1f)

        /** Returns a fresh unit vector pointing backward `(0, 0, 1)`. */
        val BACK: Vec3f get() = Vec3f(0f, 0f, 1f)
    }
}

/**
 * A mutable 4-component single-precision floating-point vector or homogeneous coordinate.
 *
 * @property x The X component of this vector.
 * @property y The Y component of this vector.
 * @property z The Z component of this vector.
 * @property w The W component of this vector.
 */
data class Vec4(var x: Float = 1f, var y: Float = 1f, var z: Float = 1f, var w: Float = 1f) {
    /**
     * Sets all four components of this vector in place.
     *
     * @param x The new X component.
     * @param y The new Y component.
     * @param z The new Z component.
     * @param w The new W component.
     */
    operator fun set(x: Float, y: Float, z: Float, w: Float) {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    /**
     * Computes the 4D scalar dot product of this vector and [other].
     *
     * @param other The vector to compute dot product with.
     * @return The scalar dot product.
     */
    fun dot(other: Vec4): Float = x * other.x + y * other.y + z * other.z + w * other.w

    /**
     * Subtracts [other] from this vector and returns the result as a new vector.
     *
     * @param other Vector to subtract.
     * @return A new [Vec4] with the difference.
     */
    operator fun minus(other: Vec4): Vec4 = Vec4(x - other.x, y - other.y, z - other.z, w - other.w)

    /**
     * `other * this` -- the matrix applied to this point, the same order the shaders use
     * (`uniforms.mvp * vec4f(inPosition, 1.0)`) and the one [Mat4.times] already composes for.
     *
     * Spelled `v * m` because that is the spelling this codebase had; the operand order in the
     * name is not the convention. It used to compute the TRANSPOSE, which made `(p * A) * B`
     * disagree with `p * (A * B)` -- the defect Vec4Test characterised, and the reason any
     * CPU-side projection through a composed MVP was wrong. Nothing on the render path noticed,
     * because matrices reach the GPU as raw floats and the multiply happens there.
     */
    operator fun times(other: Mat4): Vec4 = Vec4(
        other.m00 * x + other.m01 * y + other.m02 * z + other.m03 * w,
        other.m10 * x + other.m11 * y + other.m12 * z + other.m13 * w,
        other.m20 * x + other.m21 * y + other.m22 * z + other.m23 * w,
        other.m30 * x + other.m31 * y + other.m32 * z + other.m33 * w,
    )

    /**
     * Multiplies all four components of this vector by [scalar] and returns the result as a new vector.
     *
     * @param scalar Multiplier factor.
     * @return A new [Vec4] scaled by [scalar].
     */
    operator fun times(scalar: Float): Vec4 = Vec4(x * scalar, y * scalar, z * scalar, w * scalar)

    /**
     * Adds [other] to this vector and returns the result as a new vector.
     *
     * @param other Vector to add.
     * @return A new [Vec4] with the sum.
     */
    operator fun plus(other: Vec4): Vec4 = Vec4(x + other.x, y + other.y, z + other.z, w + other.w)

    /**
     * Computes the 3D length `sqrt(x*x + y*y + z*z)` ignoring the W component.
     *
     * @return The 3D Euclidean magnitude.
     */
    fun length3(): Float = sqrt(x * x + y * y + z * z)

    /**
     * Projects this homogeneous clip coordinate into screen pixel coordinates.
     *
     * @param screenWidth Viewport width in pixels.
     * @param screenHeight Viewport height in pixels.
     * @return Screen pixel coordinates as a 2D vector.
     */
    fun pixelCoords(screenWidth: Int, screenHeight: Int): Vec2 {
        // Convert clip coordinates to normalized device coordinates (NDC)
        val ndcX = x / w
        val ndcY = y / w

        // Convert NDC to pixel coordinates
        val pixelX = (0.5f * (ndcX + 1f) * screenWidth).toInt()
        val pixelY = (0.5f * (1f - ndcY) * screenHeight).toInt()
        return Vec2(pixelX, pixelY)
    }

    /**
     * Normalizes clip coordinates by performing perspective divide and transforms them in place into viewport pixels.
     *
     * @param viewportWidth Viewport width in pixels.
     * @param viewportHeight Viewport height in pixels.
     */
    fun makePixelCoords(viewportWidth: Int, viewportHeight: Int) {
        // Make coordinates as homogeneous
        x /= w
        y /= w
        z /= w
        w = 1.0f

        // Normalize values into NDC.
        x = 0.5f + 0.5f * x
        y = 0.5f + 0.5f * y
        z = 0.5f + 0.5f * z
        w = 1.0f

        // Move coordinates into window space (in pixels)
        x *= viewportWidth.toFloat()
        y *= viewportHeight.toFloat()
    }
}
