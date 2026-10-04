/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How two overlapping 2D shapes sit in one another, written by `penetration`: the smallest move that
 * separates them.
 *
 * Moving the shape `penetration` was called on by [normalX], [normalY] times [depth] takes it out of the
 * other. The normal has length 1 and points from the other shape toward this one. One instance is meant
 * to be kept and reused across many queries, so a frame of collision checks allocates nothing.
 */
class Overlap2 {
    /** X of the unit direction that moves the queried shape out of the other. */
    var normalX: Float = 0f
        private set

    /** Y of the unit direction that moves the queried shape out of the other. */
    var normalY: Float = 0f
        private set

    /** How far to move along the normal; zero when the shapes only touch. */
    var depth: Float = 0f
        private set

    /** Writes all three at once and returns this, as `Vec3f.set` does. */
    fun set(normalX: Float, normalY: Float, depth: Float): Overlap2 {
        this.normalX = normalX
        this.normalY = normalY
        this.depth = depth
        return this
    }

    override fun toString(): String = "Overlap2(normal=($normalX, $normalY), depth=$depth)"
}

/**
 * An axis-aligned rectangle in a plane, as its centre ([x], [y]) and half extents.
 *
 * Mutable, like `Vec3f`: `set` rewrites it in place so a game updating hundreds of colliders each frame
 * allocates none. Edges touching counts as overlapping, as it does for [Aabb.intersects]: a shape is a
 * bound, and two sharing an edge describe geometry that may well share a pixel.
 */
class Box2(
    var x: Float = 0f,
    var y: Float = 0f,
    halfWidth: Float = DEFAULT_HALF_EXTENT,
    halfHeight: Float = DEFAULT_HALF_EXTENT,
) {
    /** Half the width. Never negative. */
    var halfWidth: Float = halfWidth
        set(value) {
            require(value >= 0f) { "Box2 halfWidth must not be negative, got $value" }
            field = value
        }

    /** Half the height. Never negative. */
    var halfHeight: Float = halfHeight
        set(value) {
            require(value >= 0f) { "Box2 halfHeight must not be negative, got $value" }
            field = value
        }

    init {
        require(halfWidth >= 0f && halfHeight >= 0f) { "Box2 half extents must not be negative, got $halfWidth by $halfHeight" }
    }

    /** Rewrites the centre and half extents in place and returns this. */
    fun set(x: Float, y: Float, halfWidth: Float, halfHeight: Float): Box2 {
        this.x = x
        this.y = y
        this.halfWidth = halfWidth
        this.halfHeight = halfHeight
        return this
    }

    /** Whether this and [other] share any area, or touch. */
    fun overlaps(other: Box2): Boolean =
        abs(x - other.x) <= halfWidth + other.halfWidth && abs(y - other.y) <= halfHeight + other.halfHeight

    /** Whether this and [other] share any area, or touch. */
    fun overlaps(other: Circle2): Boolean {
        // The point of the box nearest the circle's centre, and how far that is from it.
        val dx = other.x.coerceIn(x - halfWidth, x + halfWidth) - other.x
        val dy = other.y.coerceIn(y - halfHeight, y + halfHeight) - other.y
        return dx * dx + dy * dy <= other.radius * other.radius
    }

    /**
     * Writes into [into] the smallest move that takes this box out of [other], along whichever axis needs
     * the least. Returns false, leaving [into] alone, when they do not overlap. Boxes at the same centre
     * separate along +x.
     */
    fun penetration(other: Box2, into: Overlap2): Boolean {
        val dx = x - other.x
        val dy = y - other.y
        val overlapX = halfWidth + other.halfWidth - abs(dx)
        val overlapY = halfHeight + other.halfHeight - abs(dy)
        if (overlapX < 0f || overlapY < 0f) return false
        if (overlapX <= overlapY) {
            into.set(if (dx < 0f) -1f else 1f, 0f, overlapX)
        } else {
            into.set(0f, if (dy < 0f) -1f else 1f, overlapY)
        }
        return true
    }

    /**
     * Writes into [into] the smallest move that takes this box out of [other]. Returns false, leaving
     * [into] alone, when they do not overlap. When the circle's centre is inside the box the box moves
     * along whichever axis needs the least, and the tie goes to +x, then -x, then +y.
     */
    fun penetration(other: Circle2, into: Overlap2): Boolean {
        val closestX = other.x.coerceIn(x - halfWidth, x + halfWidth)
        val closestY = other.y.coerceIn(y - halfHeight, y + halfHeight)
        val dx = closestX - other.x
        val dy = closestY - other.y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared > other.radius * other.radius) return false
        if (distanceSquared > 0f) {
            val distance = sqrt(distanceSquared)
            into.set(dx / distance, dy / distance, other.radius - distance)
        } else {
            leaveByNearestFace(other, into)
        }
        return true
    }

    /** The circle's centre is inside (or on the edge of) the box: the box leaves by whichever face is nearest. */
    private fun leaveByNearestFace(other: Circle2, into: Overlap2) {
        val pushRight = other.x + other.radius - (x - halfWidth)
        val pushLeft = x + halfWidth - (other.x - other.radius)
        val pushUp = other.y + other.radius - (y - halfHeight)
        val pushDown = y + halfHeight - (other.y - other.radius)
        val least = minOf(pushRight, pushLeft, pushUp, pushDown)
        when (least) {
            pushRight -> into.set(1f, 0f, least)
            pushLeft -> into.set(-1f, 0f, least)
            pushUp -> into.set(0f, 1f, least)
            else -> into.set(0f, -1f, least)
        }
    }

    override fun toString(): String = "Box2(centre=($x, $y), half=($halfWidth, $halfHeight))"

    private companion object {
        const val DEFAULT_HALF_EXTENT = 0.5f
    }
}

/**
 * A circle in a plane, as its centre ([x], [y]) and [radius].
 *
 * Mutable, like [Box2], and touching counts as overlapping.
 */
class Circle2(
    var x: Float = 0f,
    var y: Float = 0f,
    radius: Float = DEFAULT_RADIUS,
) {
    /** The radius. Never negative. */
    var radius: Float = radius
        set(value) {
            require(value >= 0f) { "Circle2 radius must not be negative, got $value" }
            field = value
        }

    init {
        require(radius >= 0f) { "Circle2 radius must not be negative, got $radius" }
    }

    /** Rewrites the centre and radius in place and returns this. */
    fun set(x: Float, y: Float, radius: Float): Circle2 {
        this.x = x
        this.y = y
        this.radius = radius
        return this
    }

    /** Whether this and [other] share any area, or touch. */
    fun overlaps(other: Circle2): Boolean {
        val dx = x - other.x
        val dy = y - other.y
        val reach = radius + other.radius
        return dx * dx + dy * dy <= reach * reach
    }

    /** Whether this and [other] share any area, or touch. */
    fun overlaps(other: Box2): Boolean = other.overlaps(this)

    /**
     * Writes into [into] the smallest move that takes this circle out of [other], straight away from the
     * other's centre. Returns false, leaving [into] alone, when they do not overlap. Circles at the same
     * centre separate along +x.
     */
    fun penetration(other: Circle2, into: Overlap2): Boolean {
        val dx = x - other.x
        val dy = y - other.y
        val reach = radius + other.radius
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared > reach * reach) return false
        if (distanceSquared == 0f) {
            into.set(1f, 0f, reach)
        } else {
            val distance = sqrt(distanceSquared)
            into.set(dx / distance, dy / distance, reach - distance)
        }
        return true
    }

    /**
     * Writes into [into] the smallest move that takes this circle out of [other]. Returns false, leaving
     * [into] alone, when they do not overlap. It is [Box2.penetration] seen from the circle, so the move
     * is the box's, turned around.
     */
    fun penetration(other: Box2, into: Overlap2): Boolean {
        if (!other.penetration(this, into)) return false
        into.set(-into.normalX, -into.normalY, into.depth)
        return true
    }

    override fun toString(): String = "Circle2(centre=($x, $y), radius=$radius)"

    private companion object {
        const val DEFAULT_RADIUS = 0.5f
    }
}
