/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import kotlin.math.min

/**
 * Turns walkable/blocked into distance-to-blocked, in place, with a two-pass 3-4 chamfer.
 *
 * [distance] enters holding 0 for blocked samples and [NavClearance.MAX] for walkable ones,
 * and leaves holding each walkable sample's chamfer distance to the nearest blocked one, saturated.
 * Samples beyond the array are *not* treated as blocked: a tile is baked with a margin wide enough
 * that its neighbour's obstacles are inside it, and the edge of the margin must not read as a wall.
 * The edge of the world reads as blocked because the ground there has no height, not because of
 * this.
 *
 * Two raster passes, forward then backward, each looking at the four neighbours already visited:
 * linear in the sample count, no queue, no allocation.
 *
 * @param distance Row-major `z * width + x` samples, rewritten.
 * @param width Samples along X.
 * @param depth Samples along Z.
 */
internal fun chamferClearance(distance: IntArray, width: Int, depth: Int) {
    for (z in 0 until depth) {
        for (x in 0 until width) {
            val index = z * width + x
            if (distance[index] == 0) continue
            var best = distance[index]
            best = min(best, at(distance, width, depth, x - 1, z) + ORTHOGONAL)
            best = min(best, at(distance, width, depth, x - 1, z - 1) + DIAGONAL)
            best = min(best, at(distance, width, depth, x, z - 1) + ORTHOGONAL)
            best = min(best, at(distance, width, depth, x + 1, z - 1) + DIAGONAL)
            distance[index] = best
        }
    }
    for (z in depth - 1 downTo 0) {
        for (x in width - 1 downTo 0) {
            val index = z * width + x
            if (distance[index] == 0) continue
            var best = distance[index]
            best = min(best, at(distance, width, depth, x + 1, z) + ORTHOGONAL)
            best = min(best, at(distance, width, depth, x + 1, z + 1) + DIAGONAL)
            best = min(best, at(distance, width, depth, x, z + 1) + ORTHOGONAL)
            best = min(best, at(distance, width, depth, x - 1, z + 1) + DIAGONAL)
            distance[index] = best
        }
    }
}

/** Off the array reads as saturated, so the edge contributes nothing; see [chamferClearance]. */
private fun at(distance: IntArray, width: Int, depth: Int, x: Int, z: Int): Int =
    if (x in 0 until width && z in 0 until depth) distance[z * width + x] else NavClearance.MAX

private const val ORTHOGONAL = NavClearance.UNITS_PER_SAMPLE
private const val DIAGONAL = 4
