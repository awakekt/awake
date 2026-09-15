/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.image.Bitmap
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Vec3f

/**
 * Format options for raw binary heightmap decoding and encoding.
 */
enum class RawHeightmapFormat(val bytesPerSample: Int) {
    /** 16-bit unsigned little-endian integer (standard for Unity, Unreal, World Machine). */
    Unsigned16LittleEndian(bytesPerSample = 2),

    /** 16-bit unsigned big-endian integer. */
    Unsigned16BigEndian(bytesPerSample = 2),

    /** 8-bit unsigned grayscale byte. */
    Unsigned8(bytesPerSample = 1),
}

/**
 * Pure, synchronous codec for decoding and encoding raw elevation byte buffers into [Heightmap].
 *
 * Adheres to `skills/awake-codec-and-asset-source`: contains no file I/O, network calls, or DI
 * state. Resolving external asset bytes belongs to the caller's asset source.
 */
object RawHeightmapCodec {

    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val MAX_16_BIT = 65535f
    private const val MAX_8_BIT = 255f
    private const val HALF_ROUNDING = 0.5f

    // Standard Rec. 601 grayscale luminance weights
    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f

    /**
     * Decodes raw binary heightmap bytes into an immutable [Heightmap].
     *
     * @param bytes Raw binary buffer containing row-major elevation samples.
     * @param width Sample count along the X axis.
     * @param depth Sample count along the Z axis.
     * @param scale World space grid spacing and vertical multiplier.
     * @param format Binary sample format ([RawHeightmapFormat.Unsigned16LittleEndian] by default).
     * @param minElevation Elevation mapped to the minimum raw value (0).
     * @param maxElevation Elevation mapped to the maximum raw value (65535 or 255).
     * @param origin Grid origin anchoring ([GridOrigin.Centered] by default).
     */
    fun decode(
        bytes: ByteArray,
        width: Int,
        depth: Int,
        scale: Vec3f = Vec3f(1f, 1f, 1f),
        format: RawHeightmapFormat = RawHeightmapFormat.Unsigned16LittleEndian,
        minElevation: Float = 0f,
        maxElevation: Float = 1f,
        origin: GridOrigin = GridOrigin.Centered,
    ): Heightmap {
        val totalSamples = width * depth
        val expectedBytes = totalSamples * format.bytesPerSample
        require(bytes.size >= expectedBytes) {
            "Buffer size (${bytes.size}) must be at least $expectedBytes bytes for " +
                "$width x $depth ${format.name} samples."
        }
        val elevationRange = maxElevation - minElevation
        val samples = FloatArray(totalSamples)

        when (format) {
            RawHeightmapFormat.Unsigned16LittleEndian -> decode16LittleEndian(
                bytes,
                samples,
                totalSamples,
                minElevation,
                elevationRange,
            )
            RawHeightmapFormat.Unsigned16BigEndian -> decode16BigEndian(
                bytes,
                samples,
                totalSamples,
                minElevation,
                elevationRange,
            )
            RawHeightmapFormat.Unsigned8 -> decode8Unsigned(
                bytes,
                samples,
                totalSamples,
                minElevation,
                elevationRange,
            )
        }

        return Heightmap(samples, width, depth, scale, origin)
    }

    /**
     * Decodes a [Bitmap] into an immutable [Heightmap] by extracting per-pixel luminance.
     */
    fun decodeFromBitmap(
        bitmap: Bitmap,
        scale: Vec3f = Vec3f(1f, 1f, 1f),
        minElevation: Float = 0f,
        maxElevation: Float = 1f,
        origin: GridOrigin = GridOrigin.Centered,
    ): Heightmap {
        val width = bitmap.width
        val depth = bitmap.height
        val totalSamples = width * depth
        val elevationRange = maxElevation - minElevation
        val samples = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val pixel = bitmap.pixels[i]
            val r = (pixel shr 16) and BYTE_MASK
            val g = (pixel shr BITS_PER_BYTE) and BYTE_MASK
            val b = pixel and BYTE_MASK
            val normalized = (r * LUMA_R + g * LUMA_G + b * LUMA_B) / MAX_8_BIT
            samples[i] = minElevation + normalized * elevationRange
        }

        return Heightmap(samples, width, depth, scale, origin)
    }

    /**
     * Encodes a [Heightmap] into raw 16-bit little-endian binary bytes.
     */
    fun encode16LittleEndian(
        heightmap: Heightmap,
        minElevation: Float = 0f,
        maxElevation: Float = 1f,
    ): ByteArray {
        val samples = heightmap.copySamples()
        val bytes = ByteArray(samples.size * 2)
        val elevationRange = if (maxElevation == minElevation) 1f else maxElevation - minElevation

        for (i in samples.indices) {
            val normalized = ((samples[i] - minElevation) / elevationRange).coerceIn(0f, 1f)
            val quantized = (normalized * MAX_16_BIT + HALF_ROUNDING).toInt().coerceIn(0, 0xFFFF)
            val byteOffset = i * 2
            bytes[byteOffset] = (quantized and BYTE_MASK).toByte()
            bytes[byteOffset + 1] = ((quantized ushr BITS_PER_BYTE) and BYTE_MASK).toByte()
        }

        return bytes
    }

    private fun decode16LittleEndian(
        bytes: ByteArray,
        destination: FloatArray,
        count: Int,
        minElevation: Float,
        range: Float,
    ) {
        for (i in 0 until count) {
            val offset = i * 2
            val low = bytes[offset].toInt() and BYTE_MASK
            val high = bytes[offset + 1].toInt() and BYTE_MASK
            val rawValue = (high shl BITS_PER_BYTE) or low
            val normalized = rawValue / MAX_16_BIT
            destination[i] = minElevation + normalized * range
        }
    }

    private fun decode16BigEndian(
        bytes: ByteArray,
        destination: FloatArray,
        count: Int,
        minElevation: Float,
        range: Float,
    ) {
        for (i in 0 until count) {
            val offset = i * 2
            val high = bytes[offset].toInt() and BYTE_MASK
            val low = bytes[offset + 1].toInt() and BYTE_MASK
            val rawValue = (high shl BITS_PER_BYTE) or low
            val normalized = rawValue / MAX_16_BIT
            destination[i] = minElevation + normalized * range
        }
    }

    private fun decode8Unsigned(
        bytes: ByteArray,
        destination: FloatArray,
        count: Int,
        minElevation: Float,
        range: Float,
    ) {
        for (i in 0 until count) {
            val rawValue = bytes[i].toInt() and BYTE_MASK
            val normalized = rawValue / MAX_8_BIT
            destination[i] = minElevation + normalized * range
        }
    }
}
