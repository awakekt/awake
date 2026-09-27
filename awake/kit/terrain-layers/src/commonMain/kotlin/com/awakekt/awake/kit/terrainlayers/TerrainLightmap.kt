/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset

private const val RGBA = 4
private const val NEUTRAL_LIGHT: Byte = 128.toByte()

/**
 * Baked lighting over a terrain, one texel per control texel and sampled the same way.
 *
 * RGB multiplies the surface colour: 128 leaves it unchanged, 255 roughly doubles it. Alpha is how
 * much of this replaces the scene's directional light: 255 uses only the baked light, 0 only the
 * scene's. Texels are row-major, `z * width + x`, like the control map.
 */
class TerrainLightmap(val width: Int, val depth: Int, rgba: ByteArray) {
    private val texels = rgba.copyOf()

    init {
        require(width in 1..MAX_SIDE && depth in 1..MAX_SIDE) { "A lightmap is 1..$MAX_SIDE texels a side; was $width x $depth." }
        require(rgba.size == width * depth * RGBA) { "A $width x $depth lightmap needs ${width * depth * RGBA} bytes; got ${rgba.size}." }
    }

    fun copyRgba(): ByteArray = texels.copyOf()

    fun texture(): TextureAsset = TextureAsset(texels.copyOf(), width, depth)

    companion object {
        /** Leaves colour unchanged and the scene's light in charge: what a surface without one binds. */
        val Neutral = TerrainLightmap(1, 1, byteArrayOf(NEUTRAL_LIGHT, NEUTRAL_LIGHT, NEUTRAL_LIGHT, 0))
    }
}

/**
 * `*.terrainlight`: `ATLM`, version byte, three reserved bytes, width and depth as little-endian
 * u32, then the RGBA texels. Binary for the reason the control map is: alpha carries meaning, and
 * platform image decoders may premultiply it or flip rows.
 */
object TerrainLightmapCodec {
    private val MAGIC = "ATLM".encodeToByteArray()
    private const val VERSION: Byte = 1
    private const val HEADER = 16

    fun encode(lightmap: TerrainLightmap): ByteArray {
        val rgba = lightmap.copyRgba()
        val out = ByteArray(HEADER + rgba.size)
        MAGIC.copyInto(out)
        out[4] = VERSION
        writeInt(out, 8, lightmap.width)
        writeInt(out, 12, lightmap.depth)
        rgba.copyInto(out, HEADER)
        return out
    }

    fun decode(bytes: ByteArray): TerrainLightmap {
        require(bytes.size >= HEADER && bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a terrain lightmap: missing ATLM header." }
        require(bytes[4] == VERSION) { "Terrain lightmap version ${bytes[4]} is not supported; expected $VERSION." }
        val width = readInt(bytes, 8)
        val depth = readInt(bytes, 12)
        require(width in 1..MAX_SIDE && depth in 1..MAX_SIDE) { "Terrain lightmap size $width x $depth is outside 1..$MAX_SIDE." }
        require(bytes.size == HEADER + width * depth * RGBA) {
            "A $width x $depth terrain lightmap is ${HEADER + width * depth * RGBA} bytes; got ${bytes.size}."
        }
        return TerrainLightmap(width, depth, bytes.copyOfRange(HEADER, bytes.size))
    }
}
