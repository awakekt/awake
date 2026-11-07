/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

/**
 * Per-node search state, open-addressed by packed sample key and reused across queries.
 *
 * Reuse is what the generation stamp is for: a slot belongs to this query only when its stamp
 * matches, so starting a query is one increment rather than clearing every array. The arrays grow on
 * the first queries and then stay, which is what makes a query allocation-free once warm. Nothing
 * iterates the table, so its layout cannot leak into the result's order.
 */
internal class NavNodeTable {
    private var mask = INITIAL_TABLE_CAPACITY - 1
    private var keys = LongArray(INITIAL_TABLE_CAPACITY)
    private var stamps = IntArray(INITIAL_TABLE_CAPACITY)
    private var costs = FloatArray(INITIAL_TABLE_CAPACITY)
    private var parents = LongArray(INITIAL_TABLE_CAPACITY)
    private var closed = BooleanArray(INITIAL_TABLE_CAPACITY)
    private var generation = 1
    private var size = 0

    /** Forgets every node in O(1), except once every four billion queries. */
    fun reset() {
        size = 0
        generation++
        if (generation == 0) {
            stamps.fill(0)
            generation = 1
        }
    }

    /** The slot holding [key], inserting an unvisited node at infinite cost if new to this query. */
    fun slotOf(key: Long): Int {
        if ((size + 1) * LOAD_DENOMINATOR > (mask + 1) * LOAD_NUMERATOR) grow()
        val slot = probe(key)
        if (stamps[slot] != generation) {
            stamps[slot] = generation
            keys[slot] = key
            costs[slot] = Float.MAX_VALUE
            parents[slot] = NO_PARENT
            closed[slot] = false
            size++
        }
        return slot
    }

    fun cost(slot: Int): Float = costs[slot]

    fun setCost(slot: Int, value: Float) {
        costs[slot] = value
    }

    fun parent(slot: Int): Long = parents[slot]

    fun setParent(slot: Int, value: Long) {
        parents[slot] = value
    }

    fun isClosed(slot: Int): Boolean = closed[slot]

    fun close(slot: Int) {
        closed[slot] = true
    }

    private fun probe(key: Long): Int {
        var slot = hash(key) and mask
        while (stamps[slot] == generation && keys[slot] != key) slot = (slot + 1) and mask
        return slot
    }

    private fun grow() {
        val oldKeys = keys
        val oldStamps = stamps
        val oldCosts = costs
        val oldParents = parents
        val oldClosed = closed
        val capacity = (mask + 1) * 2
        mask = capacity - 1
        keys = LongArray(capacity)
        stamps = IntArray(capacity)
        costs = FloatArray(capacity)
        parents = LongArray(capacity)
        closed = BooleanArray(capacity)
        for (old in oldKeys.indices) {
            if (oldStamps[old] != generation) continue
            val slot = probe(oldKeys[old])
            stamps[slot] = generation
            keys[slot] = oldKeys[old]
            costs[slot] = oldCosts[old]
            parents[slot] = oldParents[old]
            closed[slot] = oldClosed[old]
        }
    }

    private fun hash(key: Long): Int = ((key * HASH_MULTIPLIER) ushr HASH_SHIFT).toInt()
}

/**
 * Binary min-heap of packed keys by priority (`f`), ties broken first towards the larger cost so far
 * (`g`) and then on the key.
 *
 * The `g` tie-break is what keeps A* off plateaus: on open ground hundreds of samples share an `f`,
 * and preferring the one furthest along expands a corridor towards the goal rather than a fan. It
 * does not change which routes are optimal. The key tie-break makes the order total, so equal-cost
 * routes always pop the same way: what makes a query deterministic.
 */
internal class NavOpenHeap {
    private var keys = LongArray(INITIAL_HEAP_CAPACITY)
    private var priorities = FloatArray(INITIAL_HEAP_CAPACITY)
    private var costs = FloatArray(INITIAL_HEAP_CAPACITY)
    private var size = 0

    val isEmpty: Boolean get() = size == 0

    fun clear() {
        size = 0
    }

    fun push(key: Long, priority: Float, cost: Float) {
        if (size == keys.size) {
            keys = keys.copyOf(size * 2)
            priorities = priorities.copyOf(size * 2)
            costs = costs.copyOf(size * 2)
        }
        keys[size] = key
        priorities[size] = priority
        costs[size] = cost
        var child = size++
        while (child > 0) {
            val parent = (child - 1) / 2
            if (!precedes(child, parent)) break
            swap(child, parent)
            child = parent
        }
    }

    fun pop(): Long {
        val top = keys[0]
        size--
        keys[0] = keys[size]
        priorities[0] = priorities[size]
        costs[0] = costs[size]
        var parent = 0
        var sifting = true
        while (sifting) {
            val left = parent * 2 + 1
            val right = left + 1
            val best = when {
                left >= size -> parent
                right < size && precedes(right, left) -> right
                else -> left
            }
            sifting = best != parent && precedes(best, parent)
            if (sifting) {
                swap(best, parent)
                parent = best
            }
        }
        return top
    }

    private fun precedes(a: Int, b: Int): Boolean = when {
        priorities[a] != priorities[b] -> priorities[a] < priorities[b]
        costs[a] != costs[b] -> costs[a] > costs[b]
        else -> keys[a] < keys[b]
    }

    private fun swap(a: Int, b: Int) {
        val key = keys[a]
        keys[a] = keys[b]
        keys[b] = key
        val priority = priorities[a]
        priorities[a] = priorities[b]
        priorities[b] = priority
        val cost = costs[a]
        costs[a] = costs[b]
        costs[b] = cost
    }
}

/** Packs a sample coordinate into a positive key ordered by Z then X; see [unpackX], [unpackZ]. */
internal fun packSample(x: Int, z: Int): Long =
    ((z.toLong() + COORDINATE_BIAS) shl COORDINATE_SHIFT) or (x.toLong() + COORDINATE_BIAS)

internal fun unpackX(key: Long): Int = ((key and COORDINATE_MASK) - COORDINATE_BIAS).toInt()

internal fun unpackZ(key: Long): Int = ((key ushr COORDINATE_SHIFT) - COORDINATE_BIAS).toInt()

/** No packed key is negative, so this cannot collide with a node. */
internal const val NO_PARENT = -1L
private const val COORDINATE_SHIFT = 32
private const val COORDINATE_BIAS = 0x8000_0000L
private const val COORDINATE_MASK = 0xFFFF_FFFFL
private const val INITIAL_HEAP_CAPACITY = 256
private const val INITIAL_TABLE_CAPACITY = 1024
private const val LOAD_NUMERATOR = 3
private const val LOAD_DENOMINATOR = 4
private const val HASH_MULTIPLIER = -7046029254386353131L
private const val HASH_SHIFT = 32
