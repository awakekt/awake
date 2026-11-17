/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.clipmap

import com.awakekt.awake.asset.terrain.Heightmap
import kotlin.math.abs
import kotlin.math.roundToInt

/** A collider side this many samples long is 16 MB of floats, about four times a 513 map at 2x. */
const val DEFAULT_MAX_COLLIDER_SIDE: Int = 2049

/** How close a heightmap's spacing must be to a whole multiple of the clipmap's to count as one. */
private const val SPACING_TOLERANCE = 1e-4f

/**
 * How many clipmap level-0 cells [config] draws across one cell of this heightmap: the factor a
 * collider's samples are multiplied by so it lies where the ground is drawn (#1768).
 *
 * The clipmap draws level 0 with a vertex every [TerrainClipmapConfig.baseSpacing], each displaced
 * by the heightmap sampled bilinearly there, and splits every cell along the same diagonal a Jolt
 * heightfield does. A heightmap with 4 m spacing under a 2 m level 0 is drawn as four cells a quad,
 * with a centre vertex `(a+b+c+d)/4`; a heightfield of the raw samples splits that quad once, with a
 * centre `(a+d)/2`. Upsampled by this factor, the collider has the drawn vertices and the drawn
 * triangles.
 *
 * Returns 1, keep the raw samples, when level 0 is as coarse as the heightmap or coarser, when the
 * spacing is not a whole multiple of level 0's on both axes, or when the factor would not fit a
 * collider of [maxSide] samples a side; a factor that doesn't fit is lowered to the largest that does.
 * Level 0 lies on the heightmap's grid when its first sample's offset is a whole number of level-0
 * cells, as it is for a centred map with an odd sample count or an even factor.
 */
fun Heightmap.clipmapColliderFactor(
    config: TerrainClipmapConfig,
    maxSide: Int = DEFAULT_MAX_COLLIDER_SIDE,
): Int {
    val scale = scale
    val factorX = wholeFactor(scale.x, config.baseSpacing)
    val factorZ = wholeFactor(scale.z, config.baseSpacing)
    if (factorX <= 1 || factorX != factorZ) return 1
    val cells = maxOf(width, depth) - 1
    val fitting = (maxSide - 1) / cells
    return factorX.coerceAtMost(fitting).coerceAtLeast(1)
}

/**
 * This heightmap's samples on a grid [factor] times finer, row-major, each the bilinear height
 * [Heightmap.heightAtWorld] gives there, in sample units (before `scale.y`). The grid is
 * `(width - 1) * factor + 1` by `(depth - 1) * factor + 1`, with spacing `scale / factor`, and covers
 * the same rectangle under the same [Heightmap.origin]. Factor 1 is a copy of the samples.
 */
fun Heightmap.upsampledSamples(factor: Int): FloatArray {
    require(factor >= 1) { "factor must be at least 1; was $factor" }
    if (factor == 1) return copySamples()
    val outWidth = (width - 1) * factor + 1
    val outDepth = (depth - 1) * factor + 1
    val out = FloatArray(outWidth * outDepth)
    val source = copySamples()
    val step = 1f / factor
    for (z in 0 until outDepth) {
        val z0 = minOf(z / factor, depth - 2)
        val fz = (z - z0 * factor) * step
        for (x in 0 until outWidth) {
            val x0 = minOf(x / factor, width - 2)
            val fx = (x - x0 * factor) * step
            val row = z0 * width + x0
            val a = source[row]
            val b = source[row + 1]
            val c = source[row + width]
            val d = source[row + width + 1]
            val near = a + (b - a) * fx
            val far = c + (d - c) * fx
            out[z * outWidth + x] = near + (far - near) * fz
        }
    }
    return out
}

/** [spacing] over [base] when it is a whole number, else 1. */
private fun wholeFactor(spacing: Float, base: Float): Int {
    val ratio = spacing / base
    val whole = ratio.roundToInt()
    return if (whole >= 1 && abs(ratio - whole) <= SPACING_TOLERANCE * whole) whole else 1
}
