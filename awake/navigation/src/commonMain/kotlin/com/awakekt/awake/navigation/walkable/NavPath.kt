/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f

/** How a [NavPathQuery] answered. */
enum class NavPathStatus {
    /** Not asked yet, or cleared. The path is empty. */
    None,

    /** The path reaches the target. */
    Complete,

    /**
     * The target cannot be reached, or the search ran out of budget first: the path ends at the
     * reachable point nearest the target. It may be a single waypoint, the start, when nothing
     * reachable is nearer.
     */
    Partial,

    /** The start has nowhere to stand nearby. The path is empty. */
    NoPath,
}

/**
 * Waypoints a [NavPathQuery] writes, owned by the caller and reused query after query.
 *
 * Primitive floats in one growing array rather than a `List<Vec3f>`, so following and re-planning a
 * route allocates nothing once the array has grown to the longest route the agent has walked.
 * [toList] is there for code that wants a list and is not on a per-frame path.
 *
 * @param initialCapacity Waypoints the path holds before it first grows.
 */
class NavPath(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var coordinates = FloatArray(initialCapacity.coerceAtLeast(1) * AXES)

    /** Waypoints in the path. */
    var size: Int = 0
        internal set

    /** How the last query answered. */
    var status: NavPathStatus = NavPathStatus.None
        internal set

    /** True when there are no waypoints. */
    val isEmpty: Boolean get() = size == 0

    /**
     * World X of waypoint [index].
     *
     * @param index Waypoint index, `0 until size`.
     * @return World X.
     */
    fun x(index: Int): Float = at(index, 0)

    /**
     * World Y of waypoint [index]: the query's ground height there.
     *
     * @param index Waypoint index, `0 until size`.
     * @return World Y.
     */
    fun y(index: Int): Float = at(index, 1)

    /**
     * World Z of waypoint [index].
     *
     * @param index Waypoint index, `0 until size`.
     * @return World Z.
     */
    fun z(index: Int): Float = at(index, 2)

    /**
     * Writes waypoint [index] into [out], allocating nothing.
     *
     * @param index Waypoint index, `0 until size`.
     * @param out The vector written.
     * @return [out].
     */
    fun get(index: Int, out: Vec3f): Vec3f = out.set(x(index), y(index), z(index))

    /**
     * The waypoints as new vectors. Allocates; keep it off per-frame paths.
     *
     * @return One vector per waypoint, in order.
     */
    fun toList(): List<Vec3f> = List(size) { Vec3f(x(it), y(it), z(it)) }

    /** Empties the path and forgets its status, keeping its capacity. */
    fun clear() {
        size = 0
        status = NavPathStatus.None
    }

    internal fun add(x: Float, y: Float, z: Float) {
        if ((size + 1) * AXES > coordinates.size) coordinates = coordinates.copyOf(coordinates.size * 2)
        set(size++, x, y, z)
    }

    internal fun set(index: Int, x: Float, y: Float, z: Float) {
        val base = index * AXES
        coordinates[base] = x
        coordinates[base + 1] = y
        coordinates[base + 2] = z
    }

    internal fun reverse() {
        var low = 0
        var high = size - 1
        while (low < high) {
            for (axis in 0 until AXES) {
                val swap = coordinates[low * AXES + axis]
                coordinates[low * AXES + axis] = coordinates[high * AXES + axis]
                coordinates[high * AXES + axis] = swap
            }
            low++
            high--
        }
    }

    private fun at(index: Int, axis: Int): Float {
        if (index !in 0 until size) throw IndexOutOfBoundsException("NavPath index $index is outside 0 until $size.")
        return coordinates[index * AXES + axis]
    }
}

private const val AXES = 3
private const val DEFAULT_CAPACITY = 16
