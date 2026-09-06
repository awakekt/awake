/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.math.floor

/**
 * Coherent procedural noise generation utilities for procedural worlds, terrain, and wind fields.
 */
object Noise {

    /**
     * Samples a 2D value noise value in range [-1.0, 1.0] at coordinate ([x], [z]) for a given [seed].
     */
    fun valueNoise2D(x: Float, z: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val z0 = floor(z).toInt()
        val x1 = x0 + 1
        val z1 = z0 + 1

        val sx = smooth(x - x0)
        val sz = smooth(z - z0)

        val v00 = hash(x0, z0, seed)
        val v10 = hash(x1, z0, seed)
        val v01 = hash(x0, z1, seed)
        val v11 = hash(x1, z1, seed)

        val ix0 = lerp(v00, v10, sx)
        val ix1 = lerp(v01, v11, sx)
        return lerp(ix0, ix1, sz)
    }

    /**
     * Samples multi-octave fractal Brownian motion (fBm) noise in range [-1.0, 1.0].
     */
    fun fractalNoise2D(
        x: Float,
        z: Float,
        seed: Int,
        octaves: Int = 4,
        persistence: Float = 0.5f,
        lacunarity: Float = 2.0f,
    ): Float {
        var total = 0f
        var frequency = 1f
        var amplitude = 1f
        var maxVal = 0f

        for (i in 0 until octaves) {
            total += valueNoise2D(x * frequency, z * frequency, seed + i * 31) * amplitude
            maxVal += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }

        return if (maxVal > 0f) total / maxVal else 0f
    }

    private fun hash(x: Int, z: Int, seed: Int): Float {
        var h = seed xor (x * 374761393) xor (z * 668265263)
        h = (h xor (h shr 13)) * 1274126177
        val normalized = (h and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
        return normalized * 2f - 1f
    }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)
}
