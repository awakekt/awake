/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

import kotlin.math.sqrt

/**
 * Symmetric 4x4 plane quadrics, ten doubles per point in one flat array: `a², ab, ac, ad, b², bc, bd,
 * c², cd, d²` for a plane `ax + by + cz + d = 0`.
 */
@Suppress("LongParameterList") // Point indices and scalars, not objects, to keep the hot loop allocation-free.
internal object Quadric {
    const val SIZE = 10

    /** Adds the plane of triangle [a], [b], [c] to each of its three points. */
    fun addPlane(quadrics: DoubleArray, points: DoubleArray, a: Int, b: Int, c: Int) {
        val n = normal(points, a, b, c) ?: return
        val d = -(n[0] * points[a * 3] + n[1] * points[a * 3 + 1] + n[2] * points[a * 3 + 2])
        add(quadrics, a, n[0], n[1], n[2], d, 1.0)
        add(quadrics, b, n[0], n[1], n[2], d, 1.0)
        add(quadrics, c, n[0], n[1], n[2], d, 1.0)
    }

    fun add(quadrics: DoubleArray, p: Int, a: Double, b: Double, c: Double, d: Double, weight: Double) {
        val o = p * SIZE
        quadrics[o] += a * a * weight
        quadrics[o + 1] += a * b * weight
        quadrics[o + 2] += a * c * weight
        quadrics[o + 3] += a * d * weight
        quadrics[o + 4] += b * b * weight
        quadrics[o + 5] += b * c * weight
        quadrics[o + 6] += b * d * weight
        quadrics[o + 7] += c * c * weight
        quadrics[o + 8] += c * d * weight
        quadrics[o + 9] += d * d * weight
    }

    /** Adds point [from]'s quadric into point [into]'s. */
    fun merge(quadrics: DoubleArray, from: Int, into: Int) {
        for (i in 0 until SIZE) quadrics[into * SIZE + i] += quadrics[from * SIZE + i]
    }

    /** Point [p]'s quadric evaluated at point [at]'s position. */
    fun error(quadrics: DoubleArray, p: Int, points: DoubleArray, at: Int): Double {
        val q = p * SIZE
        val x = points[at * 3]
        val y = points[at * 3 + 1]
        val z = points[at * 3 + 2]
        val value = quadrics[q] * x * x + 2 * quadrics[q + 1] * x * y + 2 * quadrics[q + 2] * x * z + 2 * quadrics[q + 3] * x +
            quadrics[q + 4] * y * y + 2 * quadrics[q + 5] * y * z + 2 * quadrics[q + 6] * y +
            quadrics[q + 7] * z * z + 2 * quadrics[q + 8] * z + quadrics[q + 9]
        return if (value < 0.0) 0.0 else value
    }

    /** The unit normal of triangle [a], [b], [c], or null when it has no area. */
    fun normal(points: DoubleArray, a: Int, b: Int, c: Int): DoubleArray? {
        val n = cross(points, a, b, c, -1, -1)
        val length = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
        if (length < DEGENERATE) return null
        return doubleArrayOf(n[0] / length, n[1] / length, n[2] / length)
    }

    /** The unnormalized normal of triangle [a], [b], [c], with point [moved] read at point [to]'s position. */
    fun cross(points: DoubleArray, a: Int, b: Int, c: Int, moved: Int, to: Int): DoubleArray {
        val pa = if (a == moved) to else a
        val pb = if (b == moved) to else b
        val pc = if (c == moved) to else c
        val ux = points[pb * 3] - points[pa * 3]
        val uy = points[pb * 3 + 1] - points[pa * 3 + 1]
        val uz = points[pb * 3 + 2] - points[pa * 3 + 2]
        val vx = points[pc * 3] - points[pa * 3]
        val vy = points[pc * 3 + 1] - points[pa * 3 + 1]
        val vz = points[pc * 3 + 2] - points[pa * 3 + 2]
        return doubleArrayOf(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx)
    }

    private const val DEGENERATE = 1e-18
}

/** A growable list of ints, without boxing. */
internal class IntList(capacity: Int = 8) {
    internal var items = IntArray(capacity)
    var size = 0
        private set

    operator fun get(index: Int): Int = items[index]

    fun add(value: Int) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = value
    }

    fun clear() {
        size = 0
    }

    /** Keeps only the items [keep] accepts, in order. */
    inline fun retain(keep: (Int) -> Boolean) {
        var kept = 0
        for (i in 0 until size) if (keep(items[i])) items[kept++] = items[i]
        size = kept
    }
}

/** Per-point marks that clear in O(1) by moving to a new stamp. */
internal class Marks(count: Int) {
    private val stamps = IntArray(count)
    private var current = 0

    fun next(): Int = ++current

    fun set(p: Int, stamp: Int) {
        stamps[p] = stamp
    }

    fun isSet(p: Int, stamp: Int): Boolean = stamps[p] == stamp
}
