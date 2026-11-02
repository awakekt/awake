/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

/**
 * A set of grid cells, each packed into one `Long`, that answers membership without allocating.
 *
 * [WorldPartitionSystem] asks it, every frame, whether each cell around the observer is already
 * streamed; a `HashSet<WorldCellCoord>` would need a new key object for every question. Open
 * addressing with linear probing over a `LongArray`, grown at half full; removal shifts the
 * following run back, so no tombstones build up as cells come and go.
 */
internal class CellKeySet(initialCapacity: Int = MIN_CAPACITY) {
    private var keys = LongArray(capacityFor(initialCapacity)) { EMPTY }

    // The one key equal to EMPTY (x = Int.MIN_VALUE, z = 0) can't live in the table, so it is kept aside.
    private var hasEmptyKey = false

    /** Cells in the set. */
    var size: Int = 0
        private set

    fun contains(x: Int, z: Int): Boolean {
        val key = pack(x, z)
        if (key == EMPTY) return hasEmptyKey
        return keys[indexOf(key)] == key
    }

    /** Adds the cell; returns false when it was already there. */
    fun add(x: Int, z: Int): Boolean {
        val key = pack(x, z)
        val added = if (key == EMPTY) {
            !hasEmptyKey.also { hasEmptyKey = true }
        } else {
            val at = indexOf(key)
            (keys[at] != key).also { if (it) keys[at] = key }
        }
        if (added) {
            size++
            if (size * 2 > keys.size) grow()
        }
        return added
    }

    /** Removes the cell; returns false when it wasn't there. */
    fun remove(x: Int, z: Int): Boolean {
        val key = pack(x, z)
        val removed = if (key == EMPTY) {
            hasEmptyKey.also { hasEmptyKey = false }
        } else {
            val at = indexOf(key)
            (keys[at] == key).also { if (it) removeAt(at) }
        }
        if (removed) size--
        return removed
    }

    /** Empties [at] and shifts the rest of its run back, so no key sits behind an empty slot it probed past. */
    private fun removeAt(at: Int) {
        var hole = at
        keys[hole] = EMPTY
        val mask = keys.size - 1
        var next = (hole + 1) and mask
        while (keys[next] != EMPTY) {
            val home = slot(keys[next], mask)
            val between = if (hole <= next) home in (hole + 1)..next else home in (hole + 1)..mask || home <= next
            if (!between) {
                keys[hole] = keys[next]
                keys[next] = EMPTY
                hole = next
            }
            next = (next + 1) and mask
        }
    }

    fun clear() {
        keys.fill(EMPTY)
        hasEmptyKey = false
        size = 0
    }

    /** The slot holding [key], or the empty slot where it would go. */
    private fun indexOf(key: Long): Int {
        val mask = keys.size - 1
        var at = slot(key, mask)
        while (keys[at] != EMPTY && keys[at] != key) at = (at + 1) and mask
        return at
    }

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2) { EMPTY }
        for (key in old) if (key != EMPTY) keys[indexOf(key)] = key
    }

    private companion object {
        const val EMPTY = Long.MIN_VALUE
        const val MIN_CAPACITY = 64
        const val GOLDEN = -0x61c8864680b583ebL

        fun pack(x: Int, z: Int): Long = (x.toLong() shl Int.SIZE_BITS) or (z.toLong() and 0xFFFF_FFFFL)

        fun slot(key: Long, mask: Int): Int = ((key * GOLDEN) ushr Int.SIZE_BITS).toInt() and mask

        fun capacityFor(cells: Int): Int {
            var capacity = MIN_CAPACITY
            while (capacity < cells * 2) capacity *= 2
            return capacity
        }
    }
}
