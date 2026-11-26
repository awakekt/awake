/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

import android.graphics.BitmapFactory

typealias NativeBitmap = android.graphics.Bitmap
typealias NativeBitmapConfig = android.graphics.Bitmap.Config

/**
 * Android platform implementation of [Bitmap] wrapping decoded pixel buffers.
 *
 * @property width The horizontal pixel resolution.
 * @property height The vertical pixel resolution.
 * @property channel The number of color channels (4 for ARGB).
 * @property pixels Packed pixel data as an array of 32-bit integers.
 */
class AndroidBitmap(
    override val width: Int,
    override val height: Int,
    override val channel: Int,
    override val pixels: IntArray,
) : Bitmap

/**
 * Platform actual decoding encoded image [bytes] on Android via [BitmapFactory].
 *
 * @param bytes The raw encoded image byte array.
 * @return A decoded [Bitmap] instance with Y-axis flipped for texture orientation.
 */
actual suspend fun createBitmap(bytes: ByteArray): Bitmap = decodeBitmap(bytes)

private fun decodeNativeBitmap(bytes: ByteArray, mipSkip: Int): NativeBitmap {
    val sampleSize = if (mipSkip > 0) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        computeSubsampleFactor(bounds.outWidth, bounds.outHeight, mipSkip)
    } else {
        1
    }
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = NativeBitmapConfig.ARGB_8888
        inScaled = false
        inPremultiplied = false
        if (sampleSize > 1) inSampleSize = sampleSize
    }
    return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        "Failed to decode Android bitmap from ${bytes.size} byte(s)."
    }
}

private fun decodeBitmap(bytes: ByteArray, mipSkip: Int = 0): Bitmap {
    val bitmap = decodeNativeBitmap(bytes, mipSkip)
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            bitmap.getPixels(pixels, y * width, width, 0, height - 1 - y, width, 1)
        }
        AndroidBitmap(
            width = width,
            height = height,
            channel = ANDROID_RGBA_CHANNELS,
            pixels = pixels,
        )
    } finally {
        bitmap.recycle()
    }
}

private fun decodeRgba8Image(bytes: ByteArray, mipSkip: Int = 0): DecodedImage {
    val bitmap = decodeNativeBitmap(bytes, mipSkip)
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val rgba = ByteArray(width * height * ANDROID_RGBA_CHANNELS)
        val row = IntArray(width)
        val rowBytes = width * ANDROID_RGBA_CHANNELS
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, height - 1 - y, width, 1)
            packArgbRowToRgba8(row, width, rgba, y * rowBytes)
        }
        Rgba8DecodedImage(width = width, height = height, rgba8 = rgba)
    } finally {
        bitmap.recycle()
    }
}

/** Decodes straight to bottom-up RGBA8 bytes on Android without retaining a second Bitmap or full IntArray. */
actual suspend fun decodeImage(bytes: ByteArray): DecodedImage = decodeRgba8Image(bytes)

/** Decodes straight to bottom-up RGBA8 bytes on Android with [BitmapFactory.Options.inSampleSize] subsampling. */
actual suspend fun decodeImage(bytes: ByteArray, mipSkip: Int): DecodedImage = decodeRgba8Image(bytes, mipSkip)

/** This platform decodes an image on the calling thread. */
actual val canDecodeImageBlocking: Boolean = true

/** Blocking Android RGBA8 decode. */
actual fun decodeImageBlocking(bytes: ByteArray): DecodedImage = decodeRgba8Image(bytes)

/** Blocking Android RGBA8 decode with [BitmapFactory.Options.inSampleSize] subsampling. */
actual fun decodeImageBlocking(bytes: ByteArray, mipSkip: Int): DecodedImage = decodeRgba8Image(bytes, mipSkip)

private const val ANDROID_RGBA_CHANNELS = 4
