/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

/**
 * The CPU copy of what one GPU buffer holds, so an upload writes only the span that changed (#1545).
 *
 * A UI run whose span changed is staged whole, but usually only a few primitives in it moved: one
 * glyph of a chat line, one bar of a status frame. Comparing against the copy costs one read of
 * each element, less than the copy into upload memory and the `writeBuffer` it saves for every
 * unchanged element. Floats are compared by their bits, so a NaN still matches itself.
 *
 * [changedFloats] and [changedInts] return the range as `first` in the high 32 bits and the
 * exclusive end in the low 32, from [range]; an empty range (first == end) means nothing to write.
 */
internal class UploadShadow {
    private var floats = FloatArray(0)
    private var floatCount = 0
    private var ints = IntArray(0)
    private var intCount = 0

    /** Forgets the copy: the next upload writes everything, as after the buffer is reallocated. */
    fun invalidate() {
        floatCount = 0
        intCount = 0
    }

    /** The span of [data] that differs from the copy, then records [data] as the copy. */
    fun changedFloats(data: FloatArray): Long {
        val held = minOf(floatCount, data.size)
        var first = 0
        while (first < held && floats[first].toRawBits() == data[first].toRawBits()) first++
        var end = if (data.size > held) data.size else held
        if (end == held) while (end > first && floats[end - 1].toRawBits() == data[end - 1].toRawBits()) end--
        if (floats.size < data.size) floats = floats.copyOf(maxOf(data.size, floats.size * 2))
        data.copyInto(floats, first, first, end)
        floatCount = data.size
        return range(first, end)
    }

    /** [changedFloats] for indices. */
    fun changedInts(data: IntArray): Long {
        val held = minOf(intCount, data.size)
        var first = 0
        while (first < held && ints[first] == data[first]) first++
        var end = if (data.size > held) data.size else held
        if (end == held) while (end > first && ints[end - 1] == data[end - 1]) end--
        if (ints.size < data.size) ints = ints.copyOf(maxOf(data.size, ints.size * 2))
        data.copyInto(ints, first, first, end)
        intCount = data.size
        return range(first, end)
    }

    companion object {
        /** Packs [first] and the exclusive [end] of a range. */
        fun range(first: Int, end: Int): Long = (first.toLong() shl 32) or end.toLong()

        /** The first index of a packed range. */
        fun first(range: Long): Int = (range ushr 32).toInt()

        /** The element count of a packed range. */
        fun count(range: Long): Int = range.toInt() - first(range)
    }
}
