/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

/**
 * Type-safe, allocation-conscious key-value memory store for behavior trees and state machines.
 */
class Blackboard {
    private val data = mutableMapOf<String, Any>()

    /**
     * Retrieves the stored value for [key] cast to type [T], or null if absent or type mismatch.
     */
    // Safe: `as? T` performs a safe downcast and evaluates to null on type mismatch.
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: String): T? = data[key] as? T

    /**
     * Stores [value] under [key].
     */
    operator fun set(key: String, value: Any) {
        data[key] = value
    }

    /**
     * Checks whether [key] is currently stored in this blackboard.
     */
    fun contains(key: String): Boolean = data.containsKey(key)

    /**
     * Removes and returns the value associated with [key], or null if absent.
     */
    fun remove(key: String): Any? = data.remove(key)

    /**
     * Clears all stored key-value pairs from this blackboard.
     */
    fun clear() {
        data.clear()
    }
}
