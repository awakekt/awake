/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

/**
 * Represents an in-memory 2D raster image with integer-packed pixel data.
 */
interface Bitmap {
    /** The horizontal pixel resolution of the bitmap. */
    val width: Int

    /** The vertical pixel resolution of the bitmap. */
    val height: Int

    /** The number of color channels per pixel (typically 4 for ARGB/RGBA). */
    val channel: Int

    /** Packed pixel data represented as an array of 32-bit integers. */
    val pixels: IntArray
}

/**
 * Standard in-memory implementation of [Bitmap].
 *
 * @property width The horizontal pixel resolution of the bitmap.
 * @property height The vertical pixel resolution of the bitmap.
 * @property channel The number of color channels per pixel.
 * @property pixels Packed pixel data represented as an array of 32-bit integers.
 */
class DefaultBitmap(
    override val width: Int,
    override val height: Int,
    override val channel: Int,
    override val pixels: IntArray,
) : Bitmap

/**
 * Decodes compressed or raw image [bytes] into a platform-native [Bitmap] instance.
 *
 * @param bytes The raw encoded image byte array (e.g. PNG, JPEG).
 * @return A decoded [Bitmap] instance containing pixel dimensions and packed pixel data.
 */
expect suspend fun createBitmap(bytes: ByteArray): Bitmap
