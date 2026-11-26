/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

/**
 * Bounded least-recently-used (LRU) cache backed by [LinkedHashMap] with Kotlin/Wasm-safe eviction.
 *
 * Captures each entry's key and value before calling `iterator.remove()` so Kotlin/Wasm's fail-fast
 * `LinkedHashMap` entry wrapper never throws `ConcurrentModificationException` while notifying
 * [onEvict]. Supports dynamic tier-based [maxEntries] ceilings and optional pinned keys that stay
 * resident while active.
 *
 * @param K Key type.
 * @param V Value type.
 * @param maxEntries Dynamic capacity supplier evaluated on insertion and eviction; clamped to at
 *   least `1`.
 */
class BoundedLruCache<K, V>(
    private val maxEntries: () -> Int,
) {
    /**
     * Convenience constructor for a fixed [maxEntries] ceiling.
     *
     * @param maxEntries Fixed maximum number of retained entries (clamped to at least `1`).
     */
    constructor(maxEntries: Int) : this({ maxEntries })

    private val entries = LinkedHashMap<K, V>()

    /** Number of entries currently retained in the cache. */
    val size: Int get() = entries.size

    /** Whether the cache currently holds no entries. */
    fun isEmpty(): Boolean = entries.isEmpty()

    /** Whether [key] is present in the cache without affecting recency order. */
    operator fun contains(key: K): Boolean = key in entries

    /**
     * Looks up [key] and marks it most-recently-used when present.
     *
     * @param key Key to look up.
     * @return The cached value, or `null` when absent.
     */
    fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    /**
     * Removes [key] from the cache without invoking an eviction callback.
     *
     * @param key Key to remove.
     * @return The previously stored value, or `null` when absent.
     */
    fun remove(key: K): V? = entries.remove(key)

    /**
     * Inserts or refreshes [key] mapped to [value], then evicts unpinned oldest entries while
     * [size] exceeds [maxEntries].
     *
     * @param key Entry key.
     * @param value Entry value.
     * @param pinned Keys that must not be evicted during this call.
     * @param onEvict Callback invoked for each evicted `(key, value)` pair after removal.
     */
    fun put(
        key: K,
        value: V,
        pinned: Set<K> = emptySet(),
        onEvict: (K, V) -> Unit = { _, _ -> },
    ) {
        entries.remove(key)
        entries[key] = value
        evictOverLimit(pinned, onEvict)
    }

    /**
     * Evicts unpinned oldest entries while [size] exceeds the current [maxEntries] ceiling.
     *
     * @param pinned Keys that must remain retained during this eviction pass.
     * @param onEvict Callback invoked for each evicted `(key, value)` pair after removal.
     */
    fun evictOverLimit(
        pinned: Set<K> = emptySet(),
        onEvict: (K, V) -> Unit = { _, _ -> },
    ) {
        val limit = maxEntries().coerceAtLeast(1)
        if (entries.size <= limit) return
        val iterator = entries.entries.iterator()
        while (entries.size > limit && iterator.hasNext()) {
            val oldest = iterator.next()
            val key = oldest.key
            if (key in pinned) continue
            val value = oldest.value
            iterator.remove()
            onEvict(key, value)
        }
    }

    /**
     * Drops every retained entry (for example under memory pressure), notifying [onEvict] for each
     * removed pair in oldest-first order.
     *
     * @param onEvict Callback invoked for each removed `(key, value)` pair after removal.
     */
    fun trimAll(onEvict: (K, V) -> Unit = { _, _ -> }) {
        if (entries.isEmpty()) return
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.key
            val value = entry.value
            iterator.remove()
            onEvict(key, value)
        }
    }

    /** Removes all entries immediately without invoking callbacks. */
    fun clear() = entries.clear()
}
