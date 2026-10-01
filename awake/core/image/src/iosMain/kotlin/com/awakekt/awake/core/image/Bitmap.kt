/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.awakekt.awake.core.image

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

/**
 * Apple platform implementation of [Bitmap] wrapping CoreGraphics decoded pixel buffers.
 *
 * @property width The horizontal pixel resolution.
 * @property height The vertical pixel resolution.
 * @property channel The number of color channels (4 for ARGB).
 * @property pixels Packed pixel data as an array of 32-bit integers.
 */
class AppleBitmap(
    override val width: Int,
    override val height: Int,
    override val channel: Int,
    override val pixels: IntArray,
) : Bitmap

/**
 * Platform actual decoding encoded image [bytes] on Apple platforms via [NSData] and [UIImage].
 *
 * @param bytes The raw encoded image byte array.
 * @return A decoded [Bitmap] instance with Y-axis flipped for texture orientation.
 */
actual suspend fun createBitmap(bytes: ByteArray): Bitmap {
    val nsData =
        memScoped {
            NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
        }
    return createBitmap(nsData, true)
}

/**
 * Decodes raw [NSData] into a [Bitmap] instance with optional Y-axis coordinate flipping.
 *
 * @param nsData The raw image data payload.
 * @param flipY Whether to flip the Y axis for texture coordinate alignment.
 * @return A decoded [Bitmap] instance.
 */
fun createBitmap(nsData: NSData, flipY: Boolean): Bitmap {
    val image = checkNotNull(UIImage.imageWithData(nsData)) { "Invalid bitmap data" }
    return createBitmap(image, flipY)
}

/**
 * Converts a [UIImage] into a [Bitmap] instance with optional Y-axis coordinate flipping.
 *
 * @param image The UIKit image instance to convert.
 * @param flipY Whether to flip the Y axis for texture coordinate alignment.
 * @return A decoded [Bitmap] instance.
 */
fun createBitmap(image: UIImage, flipY: Boolean): Bitmap {
    val cgImage = image.CGImage
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    val pixelData = CGDataProviderCopyData(CGImageGetDataProvider(cgImage))
    val bytes = CFDataGetBytePtr(pixelData)!!
    val bytesPerRow = CGImageGetBytesPerRow(cgImage)

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val flippedY = if (flipY) height - y - 1 else y

        for (x in 0 until width) {
            val pixelInfo: Int = (width * flippedY + x) * 4
            val red = bytes[pixelInfo].toInt()
            val green = bytes[pixelInfo + 1].toInt()
            val blue = bytes[pixelInfo + 2].toInt()
            val alpha = bytes[pixelInfo + 3].toInt()

            // Convert RGBA to ARGB format
            val argbPixel = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

            // Store the pixel in the pixel array
            pixels[y * width + x] = argbPixel
        }
    }

    CFRelease(pixelData)
    return AppleBitmap(
        width = width,
        height = height,
        channel = 4,
        pixels = pixels,
    )
}
