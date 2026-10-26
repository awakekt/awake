/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import kotlin.math.sqrt

/**
 * Where each tile sits in an atlas packed in rows ("shelves"), and how large that atlas is.
 *
 * Tiles are placed tallest first, left to right, and a tile that does not fit the atlas's width starts a
 * new shelf as tall as its first, tallest, tile. The width is the multiple of [WIDTH_STEP] that gives the
 * smallest atlas. Only integer arithmetic decides, so every machine packs the same.
 *
 * `awake:core:text`'s `ShelfPacking` is this same algorithm: it lays out a font loaded at run time, and
 * `OpenTypeUiFontTest` holds the two to the same atlas. A change here is a change there.
 */
internal class ShelfPacking private constructor(
    val width: Int,
    val height: Int,
    /** Each tile's left edge, by tile index. */
    val xs: IntArray,
    /** Each tile's top edge, by tile index. */
    val ys: IntArray,
) {
    companion object {
        /** The atlas width is a multiple of this. */
        const val WIDTH_STEP = 16

        /** The widths tried run from the square root of the tiles' area up to this multiple of it. */
        private const val WIDTH_SEARCH_FACTOR = 2

        /** Packs tiles of [widths] by [heights]; both are in tile index order and positive. */
        fun pack(widths: IntArray, heights: IntArray): ShelfPacking {
            require(widths.size == heights.size) { "${widths.size} widths for ${heights.size} heights" }
            require(widths.all { it > 0 } && heights.all { it > 0 }) { "A tile needs a positive size" }
            if (widths.isEmpty()) return ShelfPacking(0, 0, IntArray(0), IntArray(0))
            val order = widths.indices.sortedWith(compareBy({ -heights[it] }, { -widths[it] }, { it }))
            val area = widths.indices.sumOf { widths[it].toLong() * heights[it] }
            val widest = roundUp(widths.max())
            val first = maxOf(widest, roundUp(integerSqrt(area)))
            var best = shelves(first, order, widths, heights)
            var width = first + WIDTH_STEP
            while (width <= first * WIDTH_SEARCH_FACTOR) {
                val trial = shelves(width, order, widths, heights)
                if (trial.width.toLong() * trial.height < best.width.toLong() * best.height) best = trial
                width += WIDTH_STEP
            }
            return best
        }

        private fun shelves(width: Int, order: List<Int>, widths: IntArray, heights: IntArray): ShelfPacking {
            val xs = IntArray(widths.size)
            val ys = IntArray(widths.size)
            var x = 0
            var y = 0
            var shelf = 0
            for (tile in order) {
                if (x + widths[tile] > width) {
                    y += shelf
                    x = 0
                    shelf = 0
                }
                xs[tile] = x
                ys[tile] = y
                x += widths[tile]
                shelf = maxOf(shelf, heights[tile])
            }
            return ShelfPacking(width, y + shelf, xs, ys)
        }

        private fun roundUp(value: Int): Int = (value + WIDTH_STEP - 1) / WIDTH_STEP * WIDTH_STEP

        /** The largest integer whose square is at most [value]. */
        private fun integerSqrt(value: Long): Int {
            var root = sqrt(value.toDouble()).toLong()
            while (root * root > value) root--
            while ((root + 1) * (root + 1) <= value) root++
            return root.toInt()
        }
    }
}
