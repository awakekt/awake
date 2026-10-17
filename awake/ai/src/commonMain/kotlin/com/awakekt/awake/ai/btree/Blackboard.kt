/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.core.math.Vec3f
import kotlinx.serialization.Serializable

/**
 * Type-safe, allocation-conscious key-value memory store for behavior trees and state machines.
 */
class Blackboard {
    private val data = mutableMapOf<String, Any>()

    /**
     * Retrieves the stored value for [key] cast to type [T], or null if absent or type mismatch.
     *
     * @param T Target type of the stored value.
     * @param key Key to query.
     * @return Stored value cast to [T], or null if absent or invalid type.
     */
    // Safe: `as? T` performs a safe downcast and evaluates to null on type mismatch.
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: String): T? = data[key] as? T

    /**
     * Stores [value] under [key].
     *
     * @param key Key to store under.
     * @param value Value to store.
     */
    operator fun set(key: String, value: Any) {
        data[key] = value
    }

    /**
     * Checks whether [key] is currently stored in this blackboard.
     *
     * @param key Key to check.
     * @return True if present, false otherwise.
     */
    fun contains(key: String): Boolean = data.containsKey(key)

    /**
     * Removes and returns the value associated with [key], or null if absent.
     *
     * @param key Key to remove.
     * @return Removed value, or null if absent.
     */
    fun remove(key: String): Any? = data.remove(key)

    /**
     * Clears all stored key-value pairs from this blackboard.
     */
    fun clear() {
        data.clear()
    }

    /** The keys stored now, in the order they were first written. */
    val keys: Set<String> get() = data.keys

    /**
     * The value under [key] as a [BlackboardValue], or `null` when it is absent or of a type a
     * blackboard value cannot hold.
     */
    fun value(key: String): BlackboardValue? = data[key]?.let(BlackboardValue::ofOrNull)

    /** Stores [value] under [key] as its plain Kotlin value (see [BlackboardValue.toRaw]). */
    fun setValue(key: String, value: BlackboardValue) {
        data[key] = value.toRaw()
    }

    /** Stores every entry of [values], as [setValue] does. */
    fun setValues(values: Map<String, BlackboardValue>) {
        for ((key, value) in values) setValue(key, value)
    }

    /**
     * Everything stored now, as data, sorted by key.
     *
     * @throws IllegalStateException naming the key when a value is of a type a snapshot cannot store
     *   (see [BlackboardValue.SUPPORTED_TYPES]). Keep such objects in a component instead.
     */
    fun snapshot(): BlackboardSnapshot {
        val values = LinkedHashMap<String, BlackboardValue>()
        for (key in data.keys.sorted()) {
            val raw = data.getValue(key)
            values[key] = BlackboardValue.ofOrNull(raw) ?: error(
                "Blackboard key '$key' holds a ${raw::class.simpleName}, which a snapshot cannot store; " +
                    "it stores ${BlackboardValue.SUPPORTED_TYPES}.",
            )
        }
        return BlackboardSnapshot(values)
    }

    /** Replaces everything stored with [snapshot]'s values. A [Vec3f] comes back as a new instance. */
    fun restore(snapshot: BlackboardSnapshot) {
        data.clear()
        setValues(snapshot.values)
    }
}

/**
 * What a [Blackboard] held, as data. Taken by [Blackboard.snapshot] and put back by
 * [Blackboard.restore].
 *
 * @property values The stored values by key.
 */
@Serializable
data class BlackboardSnapshot(val values: Map<String, BlackboardValue> = emptyMap())
