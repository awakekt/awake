/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.io.BinaryReader

/**
 * Bytes for a baked [NavSurfaceTile] and back, so a region is baked offline or once at load and
 * cached rather than re-baked every run.
 *
 * Bytes in, value out: where the bytes live (an asset, a cache directory, browser storage) is the
 * caller's, through `AssetSource` or a file system. The layout is little-endian: the magic `NAVS`,
 * a version, width and depth, sample size and origin as floats, then one clearance byte per sample,
 * row-major. A version the codec does not know, a size that does not match, or a truncated buffer
 * fails with [IllegalArgumentException] rather than decoding into a different surface.
 */
object NavSurfaceCodec {
    /** The layout version [encode] writes. */
    const val VERSION: Int = 1

    /**
     * Encodes [tile].
     *
     * @param tile The tile to encode.
     * @return Its bytes.
     */
    fun encode(tile: NavSurfaceTile): ByteArray {
        val clearance = tile.copyClearance()
        val out = ByteArray(HEADER_BYTES + clearance.size)
        var offset = 0
        for (value in intArrayOf(MAGIC, VERSION, tile.width, tile.depth)) offset = writeIntLe(out, offset, value)
        for (value in floatArrayOf(tile.sampleSize, tile.originX, tile.originZ)) offset = writeIntLe(out, offset, value.toRawBits())
        clearance.copyInto(out, offset)
        return out
    }

    /**
     * Decodes a tile [encode] wrote.
     *
     * @param bytes The encoded tile.
     * @return The tile.
     * @throws IllegalArgumentException When the bytes are not an encoded tile of a known version.
     */
    fun decode(bytes: ByteArray): NavSurfaceTile {
        val reader = BinaryReader(bytes)
        try {
            require(reader.readIntLe() == MAGIC) { "Not a navigation surface: the magic does not match." }
            val version = reader.readIntLe()
            require(version == VERSION) { "Navigation surface version $version is not supported; expected $VERSION." }
            val width = reader.readIntLe()
            val depth = reader.readIntLe()
            require(width > 0 && depth > 0 && width.toLong() * depth == (bytes.size - HEADER_BYTES).toLong()) {
                "Navigation surface is $width x $depth but holds ${bytes.size - HEADER_BYTES} samples."
            }
            val sampleSize = reader.readFloatLe()
            val originX = reader.readFloatLe()
            val originZ = reader.readFloatLe()
            return NavSurfaceTile(width, depth, sampleSize, originX, originZ, reader.readBytes(width * depth))
        } catch (truncated: IndexOutOfBoundsException) {
            throw IllegalArgumentException("Navigation surface is truncated.", truncated)
        }
    }

    private fun writeIntLe(out: ByteArray, offset: Int, value: Int): Int {
        for (byte in 0 until Int.SIZE_BYTES) out[offset + byte] = (value ushr (byte * Byte.SIZE_BITS)).toByte()
        return offset + Int.SIZE_BYTES
    }

    /** "NAVS" read as a little-endian int. */
    private const val MAGIC = 0x5356414E
    private const val HEADER_BYTES = 28
}
