/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

private val EMPTY = arrayOfNulls<Any?>(0)
private const val INITIAL_PAIRS = 2

/**
 * The properties one node declares.
 *
 * [isMergingDescendants] is the answer to "a button with a text child should be one node, not two".
 * A merging node absorbs its descendants' properties and they stop appearing separately -- every
 * `ui-headless` control has that shape, so the decision had to land before any of them are ported.
 */
class SemanticsConfiguration {
    // Keys and values side by side, in the order they were set: a node declares one to three, so a
    // scan beats a hash map, and composing one allocates a single small array (#1742).
    private var entries: Array<Any?> = EMPTY
    private var count = 0

    var isMergingDescendants: Boolean = false

    /**
     * Drops descendants' semantics rather than absorbing them.
     *
     * Implies [isMergingDescendants]: a node that replaces what is beneath it is by definition the
     * only node reported for that subtree.
     */
    var isClearingDescendants: Boolean = false

    /** The keys that have a value set. */
    val keys: Set<SemanticsPropertyKey<*>>
        get() = LinkedHashSet<SemanticsPropertyKey<*>>(count * 2).also { set ->
            for (i in 0 until count) set += keyAt(i)
        }

    /** Stores [value] under [key], replacing any earlier value. */
    operator fun <T> set(key: SemanticsPropertyKey<T>, value: T) {
        put(key, value)
    }

    @Suppress("UNCHECKED_CAST")
    /** Returns the value stored under [key], or `null` when none has been set. */
    operator fun <T> get(key: SemanticsPropertyKey<T>): T? = indexOf(key).let { if (it < 0) null else entries[it * 2 + 1] as T? }

    /** Whether a value has been set for [key]. */
    operator fun contains(key: SemanticsPropertyKey<*>): Boolean = indexOf(key) >= 0

    /**
     * Copies [other]'s properties in without overwriting what is already here.
     *
     * The merging node wins: a button labelled "Save" that contains a text reading "Save now"
     * reports "Save". An ancestor states intent; a descendant only fills gaps.
     */
    fun mergeFrom(other: SemanticsConfiguration) {
        for (i in 0 until other.count) {
            val key = other.keyAt(i)
            if (key !in this) put(key, other.entries[i * 2 + 1])
        }
    }

    /** Whether [other] declares the same flags and the same values under the same keys. */
    internal fun sameAs(other: SemanticsConfiguration): Boolean =
        isMergingDescendants == other.isMergingDescendants &&
            isClearingDescendants == other.isClearingDescendants && count == other.count && holdsAllOf(other)

    private fun holdsAllOf(other: SemanticsConfiguration): Boolean {
        for (i in 0 until other.count) {
            val at = indexOf(other.keyAt(i))
            if (at < 0 || entries[at * 2 + 1] != other.entries[i * 2 + 1]) return false
        }
        return true
    }

    private fun keyAt(index: Int): SemanticsPropertyKey<*> = entries[index * 2] as SemanticsPropertyKey<*>

    private fun indexOf(key: SemanticsPropertyKey<*>): Int {
        for (i in 0 until count) if (entries[i * 2] === key) return i
        return -1
    }

    private fun put(key: SemanticsPropertyKey<*>, value: Any?) {
        val at = indexOf(key)
        if (at >= 0) {
            entries[at * 2 + 1] = value
            return
        }
        if (count * 2 == entries.size) entries = entries.copyOf(maxOf(INITIAL_PAIRS, count * 2) * 2)
        entries[count * 2] = key
        entries[count * 2 + 1] = value
        count++
    }
}
