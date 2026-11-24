/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Primitive `codePoint -> Int` lookup table with a flat ASCII fast path and an open-addressed table
 * for higher Unicode code points. Avoids `Int` boxing on every glyph, advance, and pixel-glyph lookup.
 */
internal class CodePointIntTable(expectedEntries: Int = 0) {
    private val ascii = IntArray(ASCII_COUNT) { MISSING }
    private var keys = if (expectedEntries > 0) IntArray(tableCapacity(expectedEntries)) { EMPTY_KEY } else EMPTY_INT_ARRAY
    private var values = if (expectedEntries > 0) IntArray(keys.size) else EMPTY_INT_ARRAY
    private var extraSize = 0

    fun get(codePoint: Int): Int = when {
        codePoint in 0 until ASCII_COUNT -> ascii[codePoint]
        keys.isEmpty() -> MISSING
        else -> probe(codePoint)
    }

    fun put(codePoint: Int, value: Int) {
        if (codePoint in 0 until ASCII_COUNT) {
            ascii[codePoint] = value
        } else {
            putExtra(codePoint, value)
        }
    }

    fun putIfAbsent(codePoint: Int, value: Int) {
        if (codePoint in 0 until ASCII_COUNT) {
            if (ascii[codePoint] == MISSING) ascii[codePoint] = value
        } else {
            putExtraIfAbsent(codePoint, value)
        }
    }

    private fun putExtra(codePoint: Int, value: Int) {
        ensureExtraCapacity()
        val mask = keys.size - 1
        var slot = hash(codePoint) and mask
        while (keys[slot] != EMPTY_KEY && keys[slot] != codePoint) {
            slot = (slot + 1) and mask
        }
        if (keys[slot] == EMPTY_KEY) {
            keys[slot] = codePoint
            extraSize++
        }
        values[slot] = value
    }

    private fun putExtraIfAbsent(codePoint: Int, value: Int) {
        ensureExtraCapacity()
        val mask = keys.size - 1
        var slot = hash(codePoint) and mask
        while (keys[slot] != EMPTY_KEY && keys[slot] != codePoint) {
            slot = (slot + 1) and mask
        }
        if (keys[slot] == EMPTY_KEY) {
            keys[slot] = codePoint
            values[slot] = value
            extraSize++
        }
    }

    private fun probe(codePoint: Int): Int {
        val mask = keys.size - 1
        var slot = hash(codePoint) and mask
        var found = MISSING
        while (keys[slot] != EMPTY_KEY) {
            if (keys[slot] == codePoint) {
                found = values[slot]
                break
            }
            slot = (slot + 1) and mask
        }
        return found
    }

    private fun ensureExtraCapacity() {
        if (keys.isEmpty()) {
            keys = IntArray(INITIAL_EXTRA_CAPACITY) { EMPTY_KEY }
            values = IntArray(INITIAL_EXTRA_CAPACITY)
        } else if ((extraSize + 1) * 2 > keys.size) {
            rehash(keys.size * 2)
        }
    }

    private fun rehash(newCapacity: Int) {
        val oldKeys = keys
        val oldValues = values
        val newKeys = IntArray(newCapacity) { EMPTY_KEY }
        val newValues = IntArray(newCapacity)
        val mask = newCapacity - 1
        for (i in oldKeys.indices) {
            val k = oldKeys[i]
            if (k == EMPTY_KEY) continue
            var slot = hash(k) and mask
            while (newKeys[slot] != EMPTY_KEY) slot = (slot + 1) and mask
            newKeys[slot] = k
            newValues[slot] = oldValues[i]
        }
        keys = newKeys
        values = newValues
    }

    companion object {
        const val MISSING = -1
        private const val ASCII_COUNT = 128
        private const val EMPTY_KEY = -1
        private const val INITIAL_EXTRA_CAPACITY = 16
        private val EMPTY_INT_ARRAY = IntArray(0)

        private fun hash(codePoint: Int): Int = codePoint * HASH_MULTIPLIER

        private fun tableCapacity(expectedEntries: Int): Int {
            var cap = INITIAL_EXTRA_CAPACITY
            val needed = expectedEntries * 2
            while (cap < needed) cap = cap shl 1
            return cap
        }
    }
}

/**
 * Primitive `codePoint -> GlyphRect?` cache distinguishing cached `null` from a miss.
 * Flat array for ASCII, open-addressed primitive table for non-ASCII so neither boxes `Int`.
 */
internal class CodePointGlyphCache {
    internal val asciiValues = arrayOfNulls<GlyphRect>(ASCII_COUNT)
    internal val asciiChecked = BooleanArray(ASCII_COUNT)
    private var extraKeys = EMPTY_INT_ARRAY
    internal var extraValues = EMPTY_GLYPH_ARRAY
    private var extraSize = 0

    inline fun getOrPut(codePoint: Int, compute: () -> GlyphRect?): GlyphRect? =
        if (codePoint in 0 until ASCII_COUNT) {
            if (asciiChecked[codePoint]) {
                asciiValues[codePoint]
            } else {
                val resolved = compute()
                asciiValues[codePoint] = resolved
                asciiChecked[codePoint] = true
                resolved
            }
        } else {
            val slot = findExtraSlot(codePoint)
            if (slot >= 0) {
                extraValues[slot]
            } else {
                val resolved = compute()
                insertExtra(codePoint, resolved)
                resolved
            }
        }

    internal fun findExtraSlot(codePoint: Int): Int {
        val keys = extraKeys
        if (keys.isEmpty()) return NOT_FOUND
        val mask = keys.size - 1
        var slot = (codePoint * HASH_MULTIPLIER) and mask
        var found = NOT_FOUND
        while (keys[slot] != EMPTY_KEY) {
            if (keys[slot] == codePoint) {
                found = slot
                break
            }
            slot = (slot + 1) and mask
        }
        return found
    }

    fun insertExtra(codePoint: Int, value: GlyphRect?) {
        if (extraKeys.isEmpty()) {
            extraKeys = IntArray(INITIAL_EXTRA_CAPACITY) { EMPTY_KEY }
            extraValues = arrayOfNulls(INITIAL_EXTRA_CAPACITY)
        } else if ((extraSize + 1) * 2 > extraKeys.size) {
            rehash(extraKeys.size * 2)
        }
        val mask = extraKeys.size - 1
        var slot = (codePoint * HASH_MULTIPLIER) and mask
        while (extraKeys[slot] != EMPTY_KEY && extraKeys[slot] != codePoint) {
            slot = (slot + 1) and mask
        }
        if (extraKeys[slot] == EMPTY_KEY) extraSize++
        extraKeys[slot] = codePoint
        extraValues[slot] = value
    }

    private fun rehash(newCapacity: Int) {
        val oldKeys = extraKeys
        val oldValues = extraValues
        val newKeys = IntArray(newCapacity) { EMPTY_KEY }
        val newValues = arrayOfNulls<GlyphRect>(newCapacity)
        val mask = newCapacity - 1
        for (i in oldKeys.indices) {
            val k = oldKeys[i]
            if (k == EMPTY_KEY) continue
            var slot = (k * HASH_MULTIPLIER) and mask
            while (newKeys[slot] != EMPTY_KEY) slot = (slot + 1) and mask
            newKeys[slot] = k
            newValues[slot] = oldValues[i]
        }
        extraKeys = newKeys
        extraValues = newValues
    }

    internal companion object {
        const val ASCII_COUNT = 128
        private const val EMPTY_KEY = -1
        private const val NOT_FOUND = -1
        private const val INITIAL_EXTRA_CAPACITY = 16
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val EMPTY_GLYPH_ARRAY = emptyArray<GlyphRect?>()
    }
}

private const val HASH_MULTIPLIER = 0x9E3779B9.toInt()
