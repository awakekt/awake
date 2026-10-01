/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.math.sqrt

/**
 * A mutable 2-component vector -- screen/pixel space, mostly.
 *
 * Follows the same naming contract [Vec3] documents: bare verbs mutate and return `this`,
 * operators and `-ed` forms allocate, products and queries are pure.
 *
 * @property x Horizontal coordinate component.
 * @property y Vertical coordinate component.
 */
data class Vec2(var x: Float, var y: Float) {
    /**
     * Constructs a [Vec2] from integer [x] and [y] coordinates.
     *
     * @param x Horizontal coordinate component.
     * @param y Vertical coordinate component.
     */
    constructor(x: Int, y: Int) : this(x.toFloat(), y.toFloat())

    /** Adds [other] to this vector, returning a new [Vec2]. */
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    /** Subtracts [other] from this vector, returning a new [Vec2]. */
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    /** Multiplies this vector by [scalar], returning a new [Vec2]. */
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    /** Computes the dot product between this vector and [other]. */
    fun dot(other: Vec2): Float = x * other.x + y * other.y

    /** Computes the Euclidean length of this vector. */
    fun length(): Float = sqrt(x * x + y * y)

    /** Computes the Euclidean distance from this point to [other]. */
    fun distanceTo(other: Vec2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Distance from this point to the segment `[start, end]`, clamped to the segment's ends.
     *
     * Hit-testing a drawn line (a gizmo handle, a graph edge, a connector) is the whole reason:
     * distance to the infinite line reports a hit far past where the line was actually drawn.
     * A degenerate segment falls back to point distance rather than dividing by zero.
     */
    fun distanceToSegment(start: Vec2, end: Vec2): Float {
        val segmentX = end.x - start.x
        val segmentY = end.y - start.y
        val lengthSquared = segmentX * segmentX + segmentY * segmentY
        if (lengthSquared < DEGENERATE_SEGMENT) return distanceTo(start)
        val t = (((x - start.x) * segmentX + (y - start.y) * segmentY) / lengthSquared).coerceIn(0f, 1f)
        val closestX = start.x + segmentX * t
        val closestY = start.y + segmentY * t
        val dx = x - closestX
        val dy = y - closestY
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val DEGENERATE_SEGMENT = 1e-12f
    }
}
