/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain.splat

import com.awakekt.awake.core.image.Bitmap
import com.awakekt.awake.core.image.toRgba8Bytes

/**
 * Pure synchronous codec for decoding, creating, and normalizing [TerrainSplatWeightMap] data.
 *
 * Adheres to `skills/awake-codec-and-asset-source`: contains no file I/O or platform state.
 */
object SplatWeightMapCodec {

    private const val CHANNELS_PER_PIXEL = 4
    private const val BYTE_MASK = 0xFF
    private const val MAX_BYTE_WEIGHT = 255
    private const val HALF_ROUNDING = 0.5f

    /**
     * Decodes a raw RGBA byte buffer into a [TerrainSplatWeightMap].
     *
     * @param bytes Raw byte array of size `width * height * 4`.
     * @param width Width in texels/samples.
     * @param height Height/depth in texels/samples.
     * @param normalize When true, ensures the 4 weights at each pixel sum exactly to 255.
     */
    fun decodeRgba(
        bytes: ByteArray,
        width: Int,
        height: Int,
        normalize: Boolean = true,
    ): TerrainSplatWeightMap {
        val totalBytes = width * height * CHANNELS_PER_PIXEL
        require(bytes.size >= totalBytes) {
            "Buffer size (${bytes.size}) must be at least $totalBytes bytes for $width x $height splatmap."
        }

        val destination = if (normalize) {
            val copy = bytes.copyOf(totalBytes)
            normalizeWeights(copy, width * height)
            copy
        } else {
            bytes.copyOf(totalBytes)
        }

        return TerrainSplatWeightMap(width, height, destination)
    }

    /**
     * Decodes a [Bitmap] into a [TerrainSplatWeightMap].
     */
    fun decodeFromBitmap(
        bitmap: Bitmap,
        normalize: Boolean = true,
    ): TerrainSplatWeightMap {
        val rgbaBytes = bitmap.toRgba8Bytes()
        return decodeRgba(rgbaBytes, bitmap.width, bitmap.height, normalize)
    }

    /**
     * Creates a default uniform splat map where one layer has 100% weight (255) and others 0.
     *
     * @param width Width in texels.
     * @param height Height in texels.
     * @param defaultLayer Target layer channel index (`0` for Red, `1` for Green, `2` for Blue, `3` for Alpha).
     */
    fun createDefault(
        width: Int,
        height: Int,
        defaultLayer: Int = 0,
    ): TerrainSplatWeightMap {
        require(defaultLayer in 0 until CHANNELS_PER_PIXEL) {
            "Default layer must be between 0 and 3; was $defaultLayer."
        }
        val totalBytes = width * height * CHANNELS_PER_PIXEL
        val bytes = ByteArray(totalBytes)
        for (i in 0 until (width * height)) {
            bytes[i * CHANNELS_PER_PIXEL + defaultLayer] = MAX_BYTE_WEIGHT.toByte()
        }
        return TerrainSplatWeightMap(width, height, bytes)
    }

    /**
     * Returns an owned defensive copy of the raw RGBA bytes.
     */
    fun encodeRgba(splatMap: TerrainSplatWeightMap): ByteArray =
        splatMap.rgbaBytes.copyOf()

    private fun normalizeWeights(bytes: ByteArray, pixelCount: Int) {
        for (i in 0 until pixelCount) {
            val offset = i * CHANNELS_PER_PIXEL
            val w0 = bytes[offset].toInt() and BYTE_MASK
            val w1 = bytes[offset + 1].toInt() and BYTE_MASK
            val w2 = bytes[offset + 2].toInt() and BYTE_MASK
            val w3 = bytes[offset + 3].toInt() and BYTE_MASK
            val sum = w0 + w1 + w2 + w3

            if (sum == MAX_BYTE_WEIGHT) continue

            if (sum == 0) {
                // If all zero, default to layer 0 (Red)
                bytes[offset] = MAX_BYTE_WEIGHT.toByte()
                bytes[offset + 1] = 0
                bytes[offset + 2] = 0
                bytes[offset + 3] = 0
            } else {
                val scale = MAX_BYTE_WEIGHT.toFloat() / sum.toFloat()
                val nw0 = (w0 * scale + HALF_ROUNDING).toInt().coerceIn(0, MAX_BYTE_WEIGHT)
                val nw1 = (w1 * scale + HALF_ROUNDING).toInt().coerceIn(0, MAX_BYTE_WEIGHT)
                val nw2 = (w2 * scale + HALF_ROUNDING).toInt().coerceIn(0, MAX_BYTE_WEIGHT)
                val nw3 = (MAX_BYTE_WEIGHT - nw0 - nw1 - nw2).coerceIn(0, MAX_BYTE_WEIGHT)

                bytes[offset] = nw0.toByte()
                bytes[offset + 1] = nw1.toByte()
                bytes[offset + 2] = nw2.toByte()
                bytes[offset + 3] = nw3.toByte()
            }
        }
    }
}
