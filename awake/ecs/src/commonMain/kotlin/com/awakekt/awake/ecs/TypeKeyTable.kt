/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * Component type IDs by the platform token [componentTypeKey] gives a class: an open-addressing
 * table hashed by [componentTypeKeyHash] and probed until the token itself matches.
 *
 * On Kotlin/Wasm a reified `World.get<T>` looks its type up here every call. A `HashMap` keyed by a
 * wrapper object allocated the wrapper per call and went through the map's generic probing, which a
 * consumer's profile put at about a tenth of a frame's CPU (#1545 follow-up). Here the token is the
 * class itself, its hash is its simple name's, and two classes of one simple name share a probe
 * sequence and are told apart by comparing the classes.
 */
internal class TypeKeyTable {
    private var hashes = IntArray(INITIAL_CAPACITY)
    private var keys = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var values = arrayOfNulls<ComponentTypeId>(INITIAL_CAPACITY)

    /** Entries held. */
    var size: Int = 0
        private set

    /** The ID stored for [key], or `null`. Allocates nothing. */
    operator fun get(token: Any): ComponentTypeId? {
        val key = normalizedComponentTypeKey(token)
        val hash = componentTypeKeyHash(key)
        val mask = keys.size - 1
        var slot = hash and mask
        while (true) {
            val held = keys[slot] ?: return null
            if (hashes[slot] == hash && held == key) return values[slot]
            slot = (slot + 1) and mask
        }
    }

    /** Stores [value] for [key], which must not be held yet. */
    fun put(token: Any, value: ComponentTypeId) {
        if ((size + 1) * 2 > keys.size) grow()
        val key = normalizedComponentTypeKey(token)
        insert(componentTypeKeyHash(key), key, value)
        size += 1
    }

    fun clear() {
        keys.fill(null)
        values.fill(null)
        size = 0
    }

    private fun insert(hash: Int, key: Any, value: ComponentTypeId) {
        val mask = keys.size - 1
        var slot = hash and mask
        while (keys[slot] != null) slot = (slot + 1) and mask
        hashes[slot] = hash
        keys[slot] = key
        values[slot] = value
    }

    private fun grow() {
        val oldHashes = hashes
        val oldKeys = keys
        val oldValues = values
        hashes = IntArray(oldKeys.size * 2)
        keys = arrayOfNulls(oldKeys.size * 2)
        values = arrayOfNulls(oldKeys.size * 2)
        for (slot in oldKeys.indices) {
            val key = oldKeys[slot] ?: continue
            insert(oldHashes[slot], key, checkNotNull(oldValues[slot]))
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}

/** The hash [TypeKeyTable] files [key] under: cheap and stable for the platform's type token. */
internal expect fun componentTypeKeyHash(key: Any): Int

/** [key] as the table stores it: the platform's own token, whatever form an older caller passed. */
internal expect fun normalizedComponentTypeKey(key: Any): Any
