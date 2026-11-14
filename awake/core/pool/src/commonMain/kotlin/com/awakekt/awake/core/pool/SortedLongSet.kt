/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

import androidx.collection.MutableLongList

/**
 * A sorted set of 64-bit ids, refilled every tick without allocating: the ids an observer sees this
 * tick, say, which [diff] compares with last tick's to find what entered and what left.
 *
 * Fill it with [addUnsorted] in any order, then [sort] it once, which also drops duplicates. Reads that
 * rely on the order, [contains] and [diff], need it sorted, and refuse it otherwise. [clear] keeps the
 * buffer, so once the set has grown to its working size, filling, sorting, reading and diffing allocate
 * nothing. A tick typically swaps two sets, or [copyFrom]s this tick's into last tick's.
 *
 * It is backed by `androidx.collection`'s [MutableLongList], whose in-place sort on the JVM is the
 * JDK's: for more than a few thousand ids that arrive in long ascending or descending runs, the JDK may
 * allocate a merge buffer. Not thread-safe.
 *
 * @param initialCapacity How many ids it holds before it first grows (`>= 0`).
 */
class SortedLongSet(initialCapacity: Int = DEFAULT_CAPACITY) {
    init {
        require(initialCapacity >= 0) { "initialCapacity must be >= 0, was $initialCapacity." }
    }

    private val ids = MutableLongList(initialCapacity)

    /** Whether the ids are in ascending order with no duplicates: true after [sort], [clear] or [copyFrom] of a sorted set. */
    var isSorted: Boolean = true
        private set

    /** How many ids it holds; after [sort], how many distinct ids. */
    val size: Int get() = ids.size

    /** Whether it holds no id. */
    val isEmpty: Boolean get() = ids.isEmpty()

    /** Adds [id] in any order; the set is unsorted until the next [sort]. */
    fun addUnsorted(id: Long) {
        if (isSorted && ids.isNotEmpty() && ids[ids.lastIndex] >= id) isSorted = false
        ids.add(id)
    }

    /** Sorts the ids in place, ascending, and drops duplicates. */
    fun sort() {
        if (isSorted) return
        ids.sort()
        var kept = 0
        for (index in 0 until ids.size) {
            val id = ids[index]
            if (kept == 0 || ids[kept - 1] != id) ids[kept++] = id
        }
        if (kept < ids.size) ids.removeRange(kept, ids.size)
        isSorted = true
    }

    /**
     * Whether [id] is in the set, by binary search.
     *
     * @throws IllegalStateException When the set isn't sorted.
     */
    operator fun contains(id: Long): Boolean {
        check(isSorted) { "Sort the set before searching it." }
        return ids.binarySearch(id) >= 0
    }

    /** The id at [index], from 0 until [size]; in ascending order once sorted. */
    operator fun get(index: Int): Long = ids[index]

    /** Makes room for [capacity] ids without growing again. */
    fun ensureCapacity(capacity: Int) {
        ids.ensureCapacity(capacity)
    }

    /** Removes every id, keeping the buffer. */
    fun clear() {
        ids.clear()
        isSorted = true
    }

    /** Replaces this set's ids with [other]'s, keeping this set's buffer when it is large enough. */
    fun copyFrom(other: SortedLongSet) {
        if (other === this) return
        ids.clear()
        ids.addAll(other.ids)
        isSorted = other.isSorted
    }

    /** Defaults and the diff for [SortedLongSet]. */
    companion object {
        /** The ids a set holds before it first grows: 16. */
        const val DEFAULT_CAPACITY: Int = 16

        /**
         * Walks two sorted sets once, in ascending id order, calling [onEnter] with each id in [current]
         * and not in [previous], and [onLeave] with each id in [previous] and not in [current]. It takes
         * O(N + M) steps and allocates nothing.
         *
         * @throws IllegalStateException When either set isn't sorted.
         */
        inline fun diff(previous: SortedLongSet, current: SortedLongSet, onEnter: (Long) -> Unit, onLeave: (Long) -> Unit) {
            check(previous.isSorted && current.isSorted) { "Sort both sets before diffing them." }
            var before = 0
            var now = 0
            while (before < previous.size || now < current.size) {
                when {
                    now == current.size -> onLeave(previous[before++])
                    before == previous.size -> onEnter(current[now++])
                    previous[before] < current[now] -> onLeave(previous[before++])
                    previous[before] > current[now] -> onEnter(current[now++])
                    else -> {
                        before++
                        now++
                    }
                }
            }
        }
    }
}
