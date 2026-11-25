/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

private val EMPTY = arrayOfNulls<Any?>(0)
private const val INITIAL_OVERFLOW_PAIRS = 2
private const val INLINE_SLOTS = 3

/**
 * The properties one node declares.
 *
 * [isMergingDescendants] is the answer to "a button with a text child should be one node, not two".
 * A merging node absorbs its descendants' properties and they stop appearing separately -- every
 * `ui-headless` control has that shape, so the decision had to land before any of them are ported.
 */
class SemanticsConfiguration {
    // First three properties sit inline on the configuration itself: nearly every node declares one
    // to three (TestTag, Role, Label, Selected), so composing it allocates zero backing arrays.
    // Any fourth property and beyond spill into [overflow] as key/value pairs.
    private var key0: SemanticsPropertyKey<*>? = null
    private var val0: Any? = null
    private var key1: SemanticsPropertyKey<*>? = null
    private var val1: Any? = null
    private var key2: SemanticsPropertyKey<*>? = null
    private var val2: Any? = null
    private var overflow: Array<Any?> = EMPTY
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
    operator fun <T> get(key: SemanticsPropertyKey<T>): T? =
        indexOf(key).let { if (it < 0) null else valueAt(it) as T? }

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
            if (key !in this) put(key, other.valueAt(i))
        }
    }

    /** Whether this declares nothing but [value] under [key]: no other property and neither flag. */
    internal fun holdsOnly(key: SemanticsPropertyKey<*>, value: Any?): Boolean =
        !isMergingDescendants && !isClearingDescendants && count == 1 && key0 === key && val0 == value

    /** Whether [other] declares the same flags and the same values under the same keys. */
    internal fun sameAs(other: SemanticsConfiguration): Boolean =
        isMergingDescendants == other.isMergingDescendants &&
            isClearingDescendants == other.isClearingDescendants && count == other.count && holdsAllOf(other)

    private fun holdsAllOf(other: SemanticsConfiguration): Boolean {
        for (i in 0 until other.count) {
            val at = indexOf(other.keyAt(i))
            if (at < 0 || valueAt(at) != other.valueAt(i)) return false
        }
        return true
    }

    private fun keyAt(index: Int): SemanticsPropertyKey<*> = when (index) {
        0 -> key0!!
        1 -> key1!!
        2 -> key2!!
        else -> overflow[(index - INLINE_SLOTS) * 2] as SemanticsPropertyKey<*>
    }

    private fun valueAt(index: Int): Any? = when (index) {
        0 -> val0
        1 -> val1
        2 -> val2
        else -> overflow[(index - INLINE_SLOTS) * 2 + 1]
    }

    private fun indexOf(key: SemanticsPropertyKey<*>): Int {
        var found = when {
            count > 0 && key0 === key -> 0
            count > 1 && key1 === key -> 1
            count > 2 && key2 === key -> 2
            else -> -1
        }
        if (found < 0 && count > INLINE_SLOTS) {
            val extra = count - INLINE_SLOTS
            for (i in 0 until extra) {
                if (overflow[i * 2] === key) {
                    found = INLINE_SLOTS + i
                    break
                }
            }
        }
        return found
    }

    private fun put(key: SemanticsPropertyKey<*>, value: Any?) {
        val at = indexOf(key)
        if (at >= 0) {
            when (at) {
                0 -> val0 = value
                1 -> val1 = value
                2 -> val2 = value
                else -> overflow[(at - INLINE_SLOTS) * 2 + 1] = value
            }
            return
        }
        when (count) {
            0 -> {
                key0 = key
                val0 = value
            }
            1 -> {
                key1 = key
                val1 = value
            }
            2 -> {
                key2 = key
                val2 = value
            }
            else -> overflow = appendSemanticsOverflow(overflow, count - INLINE_SLOTS, key, value)
        }
        count++
    }
}

private fun appendSemanticsOverflow(
    overflow: Array<Any?>,
    extra: Int,
    key: SemanticsPropertyKey<*>,
    value: Any?,
): Array<Any?> {
    val target = if (extra * 2 == overflow.size) {
        overflow.copyOf(maxOf(INITIAL_OVERFLOW_PAIRS, extra * 2) * 2)
    } else {
        overflow
    }
    target[extra * 2] = key
    target[extra * 2 + 1] = value
    return target
}
