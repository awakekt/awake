/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.terrain.splat

/**
 * 4-Channel RGBA Splat Weightmap representing blend weights for up to 4 terrain materials.
 *
 * The weights at each pixel $(x, z)$ satisfy:
 * $$w_R + w_G + w_B + w_A = 255$$
 * ensuring energy-conserving diffuse blending in GPU fragment shaders.
 */
data class TerrainSplatWeightMap(
    val width: Int,
    val height: Int,
    val rgbaBytes: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "Splat weightmap dimensions must be positive; was $width x $height." }
        require(rgbaBytes.size == width * height * 4) {
            "Splat weightmap buffer size ${rgbaBytes.size} must match width * height * 4 (${width * height * 4})."
        }
    }

    /**
     * Samples the 4-channel normalized blend weights $[w_0, w_1, w_2, w_3]$ at UV $\in [0, 1]^2$.
     */
    fun sampleWeights(u: Float, v: Float): FloatArray {
        val clampedU = u.coerceIn(0f, 1f)
        val clampedV = v.coerceIn(0f, 1f)
        val px = ((clampedU * (width - 1)).toInt()).coerceIn(0, width - 1)
        val pz = ((clampedV * (height - 1)).toInt()).coerceIn(0, height - 1)
        val offset = (pz * width + px) * 4

        val r = (rgbaBytes[offset].toInt() and 0xff) / 255f
        val g = (rgbaBytes[offset + 1].toInt() and 0xff) / 255f
        val b = (rgbaBytes[offset + 2].toInt() and 0xff) / 255f
        val a = (rgbaBytes[offset + 3].toInt() and 0xff) / 255f
        return floatArrayOf(r, g, b, a)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerrainSplatWeightMap) return false
        return width == other.width &&
            height == other.height &&
            rgbaBytes.contentEquals(other.rgbaBytes)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rgbaBytes.contentHashCode()
        return result
    }
}
