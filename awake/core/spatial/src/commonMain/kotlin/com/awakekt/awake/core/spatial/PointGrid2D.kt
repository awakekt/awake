/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

import kotlin.math.floor

/**
 * Integer ids at points on a plane, for "what is within r of here" asked many times a tick.
 *
 * [SpatialGrid] indexes boxes and keeps them across frames, which is what a renderer wants. A
 * tick of gameplay usually wants something smaller: every unit's ground position put in once,
 * then a few hundred neighbourhood questions (separation, aggro, pickup range), then thrown away.
 * This grid is shaped for that: [clear], [add] each point, query with [forEachNear] or [anyNear].
 *
 * Everything lives in flat primitive arrays: an open-addressed cell table (`LongArray` keys,
 * Fibonacci hashing, linear probing) whose slots head an intrusive list of entries. [clear] is a
 * generation bump, not an array fill, and the queries are `inline`, so once the arrays have grown
 * to a tick's size a rebuild and every query allocate nothing.
 *
 * The plane is whatever two axes the caller passes, usually world X and Z. A point is a point: an
 * id may be added more than once, and each copy is visited. Use [SpatialGrid] for things with
 * extent, or for an index that lives across ticks with inserts and removals.
 *
 * @property cellSize Side of one cell. About the typical query radius is the useful size: much
 * smaller walks many empty cells, much larger tests many far points.
 * @param initialCapacity Starting point capacity before the entry arrays grow.
 */
class PointGrid2D(val cellSize: Float, initialCapacity: Int = DEFAULT_CAPACITY) {

    init {
        require(cellSize > 0f && cellSize.isFinite()) { "cellSize must be positive and finite, was $cellSize" }
        require(initialCapacity > 0) { "initialCapacity must be positive, was $initialCapacity" }
    }

    @PublishedApi internal val inverseCellSize: Float = 1f / cellSize

    /** Entries, in [add] order: id, position and the next entry in the same cell (-1 ends it). */
    @PublishedApi internal var entryIds: IntArray = IntArray(initialCapacity)

    @PublishedApi internal var entryX: FloatArray = FloatArray(initialCapacity)

    @PublishedApi internal var entryZ: FloatArray = FloatArray(initialCapacity)

    @PublishedApi internal var entryNext: IntArray = IntArray(initialCapacity)

    /** Number of points added since the last [clear]. */
    var size: Int = 0
        private set

    /** Cell table: a slot is live only when its generation is the current one. */
    private var slotKeys = LongArray(tableCapacityFor(initialCapacity))
    private var slotGenerations = IntArray(slotKeys.size)
    private var slotHeads = IntArray(slotKeys.size)
    private var tableBits = slotKeys.size.countTrailingZeroBits()
    private var generation = 1
    private var occupiedCells = 0

    /** Forgets every point. Constant time: the arrays keep their size for the next fill. */
    fun clear() {
        size = 0
        occupiedCells = 0
        generation += 1
        if (generation == Int.MAX_VALUE) {
            slotGenerations.fill(0)
            generation = 1
        }
    }

    /** Puts [id] at ([x], [z]). Allocates only while the grid grows past its largest fill. */
    fun add(id: Int, x: Float, z: Float) {
        if (size == entryIds.size) growEntries()
        if ((occupiedCells + 1) * 2 > slotKeys.size) growTable()
        val entry = size
        entryIds[entry] = id
        entryX[entry] = x
        entryZ[entry] = z
        val slot = claimSlot(cellKey(cellOf(x), cellOf(z)))
        entryNext[entry] = slotHeads[slot]
        slotHeads[slot] = entry
        size = entry + 1
    }

    /**
     * Calls [action] with the id of every point within [radius] of ([x], [z]), boundary included,
     * in no particular order.
     */
    inline fun forEachNear(x: Float, z: Float, radius: Float, action: (id: Int) -> Unit) {
        anyNear(x, z, radius) { id ->
            action(id)
            false
        }
    }

    /**
     * True when [predicate] holds for some point within [radius] of ([x], [z]); the walk stops at
     * the first one.
     */
    inline fun anyNear(x: Float, z: Float, radius: Float, predicate: (id: Int) -> Boolean): Boolean {
        require(radius >= 0f) { "radius must be non-negative, was $radius" }
        val radiusSquared = radius * radius
        val minX = cellOf(x - radius)
        val maxX = cellOf(x + radius)
        val minZ = cellOf(z - radius)
        val maxZ = cellOf(z + radius)
        val cells = (maxX.toLong() - minX + 1) * (maxZ.toLong() - minZ + 1)
        if (cells >= size) return anyNearScan(x, z, radiusSquared, predicate)
        var found = false
        cells@ for (cellZ in minZ..maxZ) {
            for (cellX in minX..maxX) {
                var entry = headOf(cellKey(cellX, cellZ))
                while (entry >= 0) {
                    if (within(entry, x, z, radiusSquared) && predicate(entryIds[entry])) {
                        found = true
                        break@cells
                    }
                    entry = entryNext[entry]
                }
            }
        }
        return found
    }

    @PublishedApi
    internal fun within(entry: Int, x: Float, z: Float, radiusSquared: Float): Boolean {
        val dx = entryX[entry] - x
        val dz = entryZ[entry] - z
        return dx * dx + dz * dz <= radiusSquared
    }

    @PublishedApi
    internal fun cellOf(coordinate: Float): Int = floor(coordinate * inverseCellSize).toInt()

    /** First entry in the cell under [key], or -1 when the cell holds nothing this generation. */
    @PublishedApi
    internal fun headOf(key: Long): Int {
        val mask = slotKeys.size - 1
        var slot = slotIndex(key)
        while (slotGenerations[slot] == generation) {
            if (slotKeys[slot] == key) return slotHeads[slot]
            slot = (slot + 1) and mask
        }
        return -1
    }

    private fun claimSlot(key: Long): Int {
        val mask = slotKeys.size - 1
        var slot = slotIndex(key)
        while (slotGenerations[slot] == generation) {
            if (slotKeys[slot] == key) return slot
            slot = (slot + 1) and mask
        }
        slotGenerations[slot] = generation
        slotKeys[slot] = key
        slotHeads[slot] = -1
        occupiedCells += 1
        return slot
    }

    /** Fibonacci hashing: the top bits of key * 2^64/phi spread packed neighbours across the table. */
    private fun slotIndex(key: Long): Int = ((key * FIBONACCI) ushr (Long.SIZE_BITS - tableBits)).toInt()

    private fun growEntries() {
        val capacity = entryIds.size * 2
        entryIds = entryIds.copyOf(capacity)
        entryX = entryX.copyOf(capacity)
        entryZ = entryZ.copyOf(capacity)
        entryNext = entryNext.copyOf(capacity)
    }

    /** Doubles the table and re-files every live cell; entry chains move with their heads. */
    private fun growTable() {
        val oldKeys = slotKeys
        val oldGenerations = slotGenerations
        val oldHeads = slotHeads
        val live = generation
        slotKeys = LongArray(oldKeys.size * 2)
        slotGenerations = IntArray(slotKeys.size)
        slotHeads = IntArray(slotKeys.size)
        tableBits = slotKeys.size.countTrailingZeroBits()
        generation = 1
        occupiedCells = 0
        for (old in oldKeys.indices) {
            if (oldGenerations[old] != live) continue
            slotHeads[claimSlot(oldKeys[old])] = oldHeads[old]
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
        const val FIBONACCI = -7046029254386353131L // 0x9E3779B97F4A7C15

        /** A power of two at least twice [entries], so the table starts at most half full. */
        fun tableCapacityFor(entries: Int): Int = maxOf(MIN_TABLE, entries.takeHighestOneBit() shl 2)

        const val MIN_TABLE = 16
    }
}

/** A reach wider than the points are many: one pass over them beats walking empty cells. */
@PublishedApi
internal inline fun PointGrid2D.anyNearScan(x: Float, z: Float, radiusSquared: Float, predicate: (id: Int) -> Boolean): Boolean {
    for (entry in 0 until size) {
        if (within(entry, x, z, radiusSquared) && predicate(entryIds[entry])) return true
    }
    return false
}

/** Packs two cell coordinates into one key, so a lookup allocates nothing. */
@PublishedApi
internal fun cellKey(cellX: Int, cellZ: Int): Long = (cellX.toLong() shl Int.SIZE_BITS) or (cellZ.toLong() and 0xFFFFFFFFL)
