/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * Component type IDs and stores by the platform token [componentTypeKey] gives a class: an
 * open-addressing table hashed by [componentTypeKeyHash] and probed until the token itself matches.
 *
 * Key and store pointers sit adjacent in an interleaved entry array so a first-probe hit reads both
 * from one cache line without entering a loop. On registration, if a small table encounters a hash
 * collision, a few odd multiplicative hash seeds are tested to keep hot worlds collision-free;
 * identical hashes (such as two classes sharing a simple name on Kotlin/Wasm) still resolve cleanly
 * via linear probing.
 */
internal class TypeKeyTable {
    private var capacity: Int = INITIAL_CAPACITY
    private var hashShift: Int = INITIAL_HASH_SHIFT
    private var hashMultiplier: Int = DEFAULT_MULTIPLIER
    private var hashes = IntArray(INITIAL_CAPACITY)

    // Interleaved [key0, store0, key1, store1, ...] so key check and store fetch share a cache line.
    private var entries = arrayOfNulls<Any>(INITIAL_CAPACITY * 2)
    private var values = arrayOfNulls<ComponentTypeId>(INITIAL_CAPACITY)
    private var typeIdValues = IntArray(INITIAL_CAPACITY) { ABSENT }

    /** Entries held. */
    var size: Int = 0
        private set

    /** The ID stored for [token], or `null`. Allocates nothing. */
    operator fun get(token: Any): ComponentTypeId? {
        val slot = findSlot(token)
        return if (slot >= 0) values[slot] else null
    }

    /** The unboxed ID value stored for [token], or `-1` when absent. Allocates nothing. */
    fun typeIdValueOrAbsent(token: Any): Int {
        val key = normalizedComponentTypeKey(token)
        val hash = componentTypeKeyHash(key)
        val slot = (hash * hashMultiplier) ushr hashShift
        val localEntries = entries
        val held = localEntries[slot shl 1]
        return when {
            held === key -> typeIdValues[slot]
            held == null -> ABSENT
            else -> {
                val found = findSlotSlow(key, hash, localEntries, slot)
                if (found >= 0) typeIdValues[found] else ABSENT
            }
        }
    }

    /** The [ComponentStore] bound to [token], or `null` if unregistered or not yet created. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> storeOrNull(token: Any): ComponentStore<T>? {
        val key = normalizedComponentTypeKey(token)
        val hash = componentTypeKeyHash(key)
        val slot = (hash * hashMultiplier) ushr hashShift
        val localEntries = entries
        val entryIndex = slot shl 1
        val held = localEntries[entryIndex]
        return when {
            held === key -> localEntries[entryIndex + 1] as ComponentStore<T>?
            held == null -> null
            else -> {
                val found = findSlotSlow(key, hash, localEntries, slot)
                if (found >= 0) localEntries[(found shl 1) + 1] as ComponentStore<T>? else null
            }
        }
    }

    /** Stores [value] for [token], which must not be held yet. */
    fun put(token: Any, value: ComponentTypeId) {
        if ((size + 1) * LOAD_FACTOR_DENOMINATOR > capacity) {
            rehash(capacity * 2, hashMultiplier)
        }
        val key = normalizedComponentTypeKey(token)
        val hash = componentTypeKeyHash(key)
        val homeSlot = (hash * hashMultiplier) ushr hashShift
        if (entries[homeSlot shl 1] != null && capacity <= MAX_PERFECT_CAPACITY) {
            var candidate = hashMultiplier
            repeat(MAX_SEED_ATTEMPTS) {
                candidate = (candidate + SEED_STEP) or 1
                if (isCollisionFree(candidate, hash)) {
                    rehash(capacity, candidate)
                    insert(hash, key, value, store = null)
                    size += 1
                    return
                }
            }
        }
        insert(hash, key, value, store = null)
        size += 1
    }

    /** Attaches [store] to the already-registered [token] so lookups reach it in a single probe. */
    fun bindStore(token: Any, store: ComponentStore<Any>) {
        val slot = findSlot(token)
        if (slot >= 0) entries[(slot shl 1) + 1] = store
    }

    fun clear() {
        entries.fill(null)
        values.fill(null)
        typeIdValues.fill(ABSENT)
        size = 0
    }

    private fun findSlot(token: Any): Int {
        val key = normalizedComponentTypeKey(token)
        val hash = componentTypeKeyHash(key)
        return findSlotSlow(key, hash, entries, (hash * hashMultiplier) ushr hashShift)
    }

    private fun findSlotSlow(key: Any, hash: Int, localEntries: Array<Any?>, startSlot: Int): Int {
        val mask = capacity - 1
        var slot = startSlot
        while (true) {
            val held = localEntries[slot shl 1]
            val found = held === key || (hashes[slot] == hash && held == key)
            if (held == null || found) return if (held == null) ABSENT else slot
            slot = (slot + 1) and mask
        }
    }

    private fun insert(hash: Int, key: Any, value: ComponentTypeId, store: ComponentStore<Any>?) {
        val mask = capacity - 1
        var slot = (hash * hashMultiplier) ushr hashShift
        while (entries[slot shl 1] != null) slot = (slot + 1) and mask
        hashes[slot] = hash
        val entryIndex = slot shl 1
        entries[entryIndex] = key
        entries[entryIndex + 1] = store
        values[slot] = value
        typeIdValues[slot] = value.value
    }

    private fun rehash(newCapacity: Int, newMultiplier: Int) {
        val oldCapacity = capacity
        val oldHashes = hashes
        val oldEntries = entries
        val oldValues = values
        capacity = newCapacity
        hashShift = Int.SIZE_BITS - newCapacity.countTrailingZeroBits()
        hashMultiplier = newMultiplier
        hashes = IntArray(newCapacity)
        entries = arrayOfNulls(newCapacity * 2)
        values = arrayOfNulls(newCapacity)
        typeIdValues = IntArray(newCapacity) { ABSENT }
        for (slot in 0 until oldCapacity) {
            val entryIndex = slot shl 1
            val key = oldEntries[entryIndex] ?: continue

            @Suppress("UNCHECKED_CAST")
            val store = oldEntries[entryIndex + 1] as ComponentStore<Any>?
            insert(oldHashes[slot], key, checkNotNull(oldValues[slot]), store)
        }
    }

    private fun isCollisionFree(multiplier: Int, newHash: Int): Boolean {
        val shift = hashShift
        val words = LongArray(MAX_PERFECT_CAPACITY ushr 6)
        val firstSlot = (newHash * multiplier) ushr shift
        words[firstSlot ushr 6] = 1L shl firstSlot
        for (slot in 0 until capacity) {
            if (entries[slot shl 1] != null) {
                val mapped = (hashes[slot] * multiplier) ushr shift
                val word = mapped ushr 6
                val bit = 1L shl mapped
                if (words[word] and bit != 0L) return false
                words[word] = words[word] or bit
            }
        }
        return true
    }

    private companion object {
        const val ABSENT = -1
        const val INITIAL_CAPACITY = 32
        const val INITIAL_HASH_SHIFT = 27
        const val LOAD_FACTOR_DENOMINATOR = 4
        const val MAX_PERFECT_CAPACITY = 256
        const val MAX_SEED_ATTEMPTS = 32
        const val DEFAULT_MULTIPLIER = -0x61c88647 // 0x9E3779B9 (odd golden-ratio constant)
        const val SEED_STEP = 0x27d4eb2d
    }
}

/** The hash [TypeKeyTable] files [key] under: cheap and stable for the platform's type token. */
internal expect fun componentTypeKeyHash(key: Any): Int

/** [key] as the table stores it: the platform's own token, whatever form an older caller passed. */
internal expect fun normalizedComponentTypeKey(key: Any): Any
