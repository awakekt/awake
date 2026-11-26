/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.spatial

/**
 * Compact, allocation-free set of non-negative integer IDs backed by a generation stamp array.
 *
 * [clear] runs in O(1) by advancing a stamp counter rather than zeroing storage, and [add]/[contains]
 * accept primitive [Int] arguments without boxing or hashing on JVM or Kotlin/Wasm.
 */
class IntIdSet(initialCapacity: Int = DEFAULT_CAPACITY) {
    private var stamps = IntArray(initialCapacity.coerceAtLeast(1))
    private var stamp = 1

    /** Number of distinct IDs currently present in this set. */
    var size: Int = 0
        private set

    /** True when this set holds no IDs. */
    val isEmpty: Boolean get() = size == 0

    /** Empties this set in O(1) without clearing backing storage (except on integer wraparound). */
    fun clear() {
        size = 0
        stamp += 1
        if (stamp == 0) {
            stamps.fill(0)
            stamp = 1
        }
    }

    /** Adds non-negative [id] to this set, growing storage if needed. Returns true when newly added. */
    fun add(id: Int): Boolean {
        require(id >= 0) { "IntIdSet requires non-negative IDs, got $id." }
        if (id >= stamps.size) {
            stamps = stamps.copyOf(maxOf(stamps.size * 2, id + 1))
        }
        if (stamps[id] == stamp) return false
        stamps[id] = stamp
        size += 1
        return true
    }

    /** True when [id] is currently in this set. */
    operator fun contains(id: Int): Boolean =
        id >= 0 && id < stamps.size && stamps[id] == stamp

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
