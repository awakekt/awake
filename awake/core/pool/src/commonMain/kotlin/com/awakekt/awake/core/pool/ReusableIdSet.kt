/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

/**
 * The ids in use from `0 until maxIds`, handing out the smallest free one, so ids stay small and are
 * reused once released: network entity ids, slot indices, handles into parallel arrays.
 *
 * It is a bit set, one bit per id, so it allocates nothing once it has grown to the highest id in use;
 * [allocate], [release] and [contains] are constant time apart from the scan for a free id, which
 * skips 64 ids at a time. [forEach] visits the ids in use in ascending order without an iterator. Not
 * thread-safe.
 *
 * @param initialCapacity How many ids it holds before it first grows (`>= 1`).
 * @property maxIds How many ids it can hand out: the ids are `0 until maxIds` (`>= 1`).
 */
class ReusableIdSet(initialCapacity: Int = DEFAULT_CAPACITY, val maxIds: Int = Int.MAX_VALUE) {
    init {
        require(initialCapacity >= 1) { "initialCapacity must be >= 1, was $initialCapacity." }
        require(maxIds >= 1) { "maxIds must be >= 1, was $maxIds." }
    }

    // One bit per id. Published for the inline forEach; not part of the API.
    @PublishedApi
    internal var words: LongArray = LongArray(wordsFor(minOf(initialCapacity, maxIds)))

    // No word before this one has a free bit.
    private var firstFreeWord = 0

    /** How many ids are in use. */
    var size: Int = 0
        private set

    /** Whether no id is in use. */
    val isEmpty: Boolean get() = size == 0

    /** Whether every id below [maxIds] is in use, so [allocate] would fail. */
    val isFull: Boolean get() = size == maxIds

    /**
     * Marks the smallest free id as in use and returns it.
     *
     * @throws IllegalStateException When all [maxIds] ids are in use.
     */
    fun allocate(): Int {
        val id = tryAllocate()
        check(id >= 0) { "All $maxIds ids are in use." }
        return id
    }

    /** [allocate], or -1 when all [maxIds] ids are in use. */
    fun tryAllocate(): Int {
        if (isFull) return NONE
        var word = firstFreeWord
        while (word < words.size && words[word] == FULL_WORD) word++
        if (word == words.size) grow()
        val bit = words[word].inv().countTrailingZeroBits()
        val id = word * BITS + bit
        // Only reachable when the free bit is past maxIds in the last word, which isFull rules out.
        check(id < maxIds) { "All $maxIds ids are in use." }
        words[word] = words[word] or (1L shl bit)
        firstFreeWord = word
        size++
        return id
    }

    /**
     * Marks [id] as in use, for ids that arrive from elsewhere, such as a snapshot.
     *
     * @return False when it was already in use.
     * @throws IllegalArgumentException When [id] is outside `0 until maxIds`.
     */
    fun add(id: Int): Boolean {
        require(id in 0 until maxIds) { "id must be in 0 until $maxIds, was $id." }
        while (id / BITS >= words.size) grow()
        val word = id / BITS
        val mask = 1L shl (id % BITS)
        if ((words[word] and mask) != 0L) return false
        words[word] = words[word] or mask
        size++
        return true
    }

    /** Frees [id] for reuse. Returns false when it wasn't in use, an id out of range included. */
    fun release(id: Int): Boolean {
        if (!contains(id)) return false
        val word = id / BITS
        words[word] = words[word] and (1L shl (id % BITS)).inv()
        if (word < firstFreeWord) firstFreeWord = word
        size--
        return true
    }

    /** Whether [id] is in use. */
    operator fun contains(id: Int): Boolean {
        if (id < 0) return false
        val word = id / BITS
        return word < words.size && (words[word] and (1L shl (id % BITS))) != 0L
    }

    /** Frees every id, keeping the storage. */
    fun clear() {
        words.fill(0L)
        firstFreeWord = 0
        size = 0
    }

    /** Calls [action] with each id in use, in ascending order, allocating nothing. */
    inline fun forEach(action: (Int) -> Unit) {
        val bitsPerWord = Long.SIZE_BITS
        val storage = words
        for (index in storage.indices) {
            var bits = storage[index]
            while (bits != 0L) {
                action(index * bitsPerWord + bits.countTrailingZeroBits())
                bits = bits and (bits - 1)
            }
        }
    }

    private fun grow() {
        val most = wordsFor(maxIds)
        check(words.size < most) { "All $maxIds ids are in use." }
        val next = if (words.size > most / 2) most else words.size * 2
        words = words.copyOf(maxOf(next, words.size + 1))
    }

    /** Defaults and sentinels for [ReusableIdSet]. */
    companion object {
        /** The ids a set holds before it first grows: 64. */
        const val DEFAULT_CAPACITY: Int = 64

        /** What [tryAllocate] returns when every id is in use. */
        const val NONE: Int = -1

        private const val BITS = Long.SIZE_BITS
        private const val FULL_WORD = -1L

        private fun wordsFor(ids: Int): Int = (ids - 1) / BITS + 1
    }
}
