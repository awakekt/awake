/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.floor

/**
 * Zero-allocation 2D spatial hash grid partitioning ground-plane `(x, z)` points into square cells
 * [cellMetres] wide.
 *
 * Uses Fibonacci hashing and linear probing over primitive [LongArray] and [IntArray] tables with
 * pooled cell buckets so rebuilding (`clear` + `add`) and querying (`forEachNear` / `anyNear`)
 * allocate nothing on the frame thread once warmed up.
 *
 * @property cellMetres Width and depth of each square grid cell in world units (metres).
 * @param initialCapacity Initial hash-table slot capacity (rounded up to a power of two).
 */
class SpatialHashGrid2D(
    val cellMetres: Float = DEFAULT_CELL_METRES,
    initialCapacity: Int = DEFAULT_INITIAL_SLOTS,
) {
    init {
        require(cellMetres > 0f) { "Cell size must be positive, not $cellMetres." }
        require(initialCapacity > 0) { "Initial capacity must be positive, not $initialCapacity." }
    }

    private var bits = initialBitsFor(initialCapacity)
    private var keys = LongArray(1 shl bits)
    private var slots = IntArray(1 shl bits)
    private var shift = Long.SIZE_BITS - bits

    private val cells = ArrayList<Bucket>()
    private var used = 0

    /** Total number of points currently stored in the grid. */
    var size: Int = 0
        private set

    /**
     * Clears all stored points while retaining allocated cell buckets and table arrays for reuse on
     * subsequent frames.
     */
    fun clear() {
        if (used == 0) return
        slots.fill(0)
        used = 0
        size = 0
    }

    /**
     * Inserts point [id] at ground coordinates `([x], [z])`.
     *
     * @param id Caller-assigned integer identifier or index for the point.
     * @param x World X coordinate.
     * @param z World Z coordinate.
     */
    fun add(id: Int, x: Float, z: Float) {
        val key = key(cellOf(x), cellOf(z))
        var at = slotOf(key)
        while (slots[at] != 0 && keys[at] != key) {
            at = (at + 1) and (slots.size - 1)
        }
        if (slots[at] == 0) {
            if ((used + 1) * 2 > slots.size) {
                grow()
                return add(id, x, z)
            }
            if (used == cells.size) cells += Bucket()
            cells[used].size = 0
            keys[at] = key
            slots[at] = ++used
        }
        cells[slots[at] - 1].add(id)
        size++
    }

    /**
     * Invokes [action] for the `id` of every point stored in grid cells overlapping [radius] around
     * `([x], [z])`, visiting the query point's own cell first.
     *
     * Because entire candidate cells are visited, callers performing exact circular range checks
     * should still verify exact Euclidean distance inside [action].
     *
     * @param x Query center X coordinate.
     * @param z Query center Z coordinate.
     * @param radius Search radius in world units.
     * @param action Callback invoked with each candidate point identifier.
     */
    inline fun forEachNear(x: Float, z: Float, radius: Float, action: (Int) -> Unit) {
        val homeX = cellOf(x)
        val homeZ = cellOf(z)
        bucketAt(homeX, homeZ)?.let { bucket ->
            for (i in 0 until bucket.size) action(bucket.items[i])
        }
        val r = radius + radius * SLACK + SLACK
        val fromX = cellOf(x - r)
        val toX = cellOf(x + r)
        val fromZ = cellOf(z - r)
        val toZ = cellOf(z + r)
        for (cx in fromX..toX) {
            for (cz in fromZ..toZ) {
                val bucket = if (cx == homeX && cz == homeZ) null else bucketAt(cx, cz)
                for (i in 0 until (bucket?.size ?: 0)) action(bucket!!.items[i])
            }
        }
    }

    /**
     * Returns `true` as soon as [predicate] returns `true` for any point in grid cells overlapping
     * [radius] around `([x], [z])`.
     *
     * @param x Query center X coordinate.
     * @param z Query center Z coordinate.
     * @param radius Search radius in world units.
     * @param predicate Predicate tested against each candidate point identifier.
     * @return `true` if any candidate satisfies [predicate], `false` otherwise.
     */
    inline fun anyNear(x: Float, z: Float, radius: Float, predicate: (Int) -> Boolean): Boolean {
        forEachNear(x, z, radius) { if (predicate(it)) return true }
        return false
    }

    @PublishedApi
    internal fun cellOf(at: Float): Int = floor(at / cellMetres).toInt()

    @PublishedApi
    internal fun bucketAt(cx: Int, cz: Int): Bucket? {
        if (used == 0) return null
        val key = key(cx, cz)
        var at = slotOf(key)
        var found: Bucket? = null
        while (slots[at] != 0) {
            if (keys[at] == key) {
                found = cells[slots[at] - 1]
                break
            }
            at = (at + 1) and (slots.size - 1)
        }
        return found
    }

    private fun key(cx: Int, cz: Int): Long =
        (cx.toLong() shl Int.SIZE_BITS) or (cz.toLong() and 0xFFFF_FFFFL)

    private fun slotOf(key: Long): Int = ((key * GOLDEN) ushr shift).toInt()

    private fun grow() {
        val oldKeys = keys
        val oldSlots = slots
        keys = LongArray(oldKeys.size * 2)
        slots = IntArray(oldSlots.size * 2)
        shift--
        for (i in oldSlots.indices) {
            if (oldSlots[i] == 0) continue
            var at = slotOf(oldKeys[i])
            while (slots[at] != 0) at = (at + 1) and (slots.size - 1)
            keys[at] = oldKeys[i]
            slots[at] = oldSlots[i]
        }
    }

    @PublishedApi
    internal class Bucket {
        var items = IntArray(INITIAL_ITEMS)
        var size = 0

        fun add(id: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = id
        }
    }

    @PublishedApi
    internal companion object {
        const val DEFAULT_CELL_METRES = 16f
        const val DEFAULT_INITIAL_SLOTS = 64
        const val INITIAL_ITEMS = 8
        const val SLACK = 1e-3f
        const val GOLDEN = -7046029254386353131L

        private fun initialBitsFor(capacity: Int): Int {
            val clamped = capacity.coerceAtLeast(4)
            return (Int.SIZE_BITS - (clamped - 1).countLeadingZeroBits()).coerceIn(2, 30)
        }
    }
}
