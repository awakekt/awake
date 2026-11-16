/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.math.max
import kotlin.math.min

/**
 * Sorts an [ArrayList] in place, stably, using one kept scratch array so neither the JVM (with
 * escape analysis off) nor Kotlin/Wasm allocates a merge buffer or copy per frame (#1555).
 *
 * [sortWith] takes the comparison inline, and is what a frame's draws sort through: on Kotlin/Wasm a
 * call to a [Comparator] allocated about 27 B each, about 320 B a draw over a 3,000-draw sort (#1621).
 */
internal class StableListSort {
    @PublishedApi
    internal var scratch: Array<Any?> = arrayOfNulls(INITIAL_SCRATCH)

    /** Sorts [list] by [comparator]; a frame's own sorts use [sortWith], which calls no [Comparator]. */
    fun <T : Any> sort(list: ArrayList<T>, comparator: Comparator<in T>) {
        sortWith(list) { a, b -> comparator.compare(a, b) }
    }

    /** Sorts [list] by [compare], inlined at the call: runs of [RUN] by insertion, then merged. */
    inline fun <T : Any> sortWith(list: ArrayList<T>, compare: (T, T) -> Int) {
        val size = list.size
        if (size <= 1) return
        var start = 0
        while (start < size) {
            insertionSort(list, start, min(start + RUN, size), compare)
            start += RUN
        }
        if (size <= RUN) return
        var width = RUN
        var used = 0
        while (width < size) {
            var left = 0
            while (left + width < size) {
                val mid = left + width
                val right = min(mid + width, size)
                if (compare(list[mid - 1], list[mid]) > 0) {
                    used = max(used, mid - left)
                    merge(list, left, mid, right, compare)
                }
                left = right
            }
            width *= 2
        }
        if (used > 0) scratch.fill(null, 0, used)
    }

    @PublishedApi
    internal inline fun <T : Any> insertionSort(list: ArrayList<T>, start: Int, end: Int, compare: (T, T) -> Int) {
        for (index in start + 1 until end) {
            val moving = list[index]
            var at = index - 1
            while (at >= start && compare(list[at], moving) > 0) {
                list[at + 1] = list[at]
                at--
            }
            list[at + 1] = moving
        }
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal inline fun <T : Any> merge(list: ArrayList<T>, left: Int, mid: Int, right: Int, compare: (T, T) -> Int) {
        val leftSize = mid - left
        if (scratch.size < leftSize) scratch = arrayOfNulls(max(leftSize, scratch.size * 2))
        val buffer = scratch
        for (index in 0 until leftSize) buffer[index] = list[left + index]
        var i = 0
        var j = mid
        var out = left
        while (i < leftSize && j < right) {
            val fromLeft = buffer[i] as T
            val fromRight = list[j]
            if (compare(fromLeft, fromRight) <= 0) {
                list[out++] = fromLeft
                i++
            } else {
                list[out++] = fromRight
                j++
            }
        }
        while (i < leftSize) list[out++] = buffer[i++] as T
    }

    /** The run length, and the scratch's starting size. */
    companion object {
        /** Runs sorted by insertion before merging. */
        const val RUN = 16

        /** The scratch array's starting size; it grows to the largest merge's left half. */
        const val INITIAL_SCRATCH = 128
    }
}
