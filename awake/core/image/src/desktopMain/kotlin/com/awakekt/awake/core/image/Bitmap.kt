/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Platform actual decoding encoded image [bytes] on Desktop JVM via [decodeBitmap].
 *
 * @param bytes The raw encoded image byte array.
 * @return A decoded [Bitmap] instance.
 */
actual suspend fun createBitmap(bytes: ByteArray): Bitmap = decodeBitmap(bytes)

/**
 * Synchronously decodes encoded image [bytes] on Desktop JVM into a [Bitmap].
 *
 * @param bytes The raw encoded image byte array.
 * @return A decoded [Bitmap] instance with inverted Y-coordinates for texture mapping.
 */
fun decodeBitmap(bytes: ByteArray): Bitmap = bufferedImageToBitmap(ImageIO.read(ByteArrayInputStream(bytes)))

private fun decodeBitmapSubsampled(bytes: ByteArray, mipSkip: Int): Bitmap {
    val subsampled = if (mipSkip > 0) readSubsampledImage(bytes, mipSkip) else null
    return if (subsampled != null) bufferedImageToBitmap(subsampled) else decodeBitmap(bytes)
}

private fun readSubsampledImage(bytes: ByteArray, mipSkip: Int): BufferedImage? {
    val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    return stream.use { input ->
        val readers = ImageIO.getImageReaders(input)
        val reader = if (readers.hasNext()) readers.next() else return@use null
        try {
            reader.setInput(input, true, true)
            val factor = computeSubsampleFactor(reader.getWidth(0), reader.getHeight(0), mipSkip)
            val param = reader.defaultReadParam
            if (factor > 1) param.setSourceSubsampling(factor, factor, 0, 0)
            reader.read(0, param)
        } finally {
            reader.dispose()
        }
    }
}

private fun bufferedImageToBitmap(bufferedImage: BufferedImage): Bitmap {
    val width = bufferedImage.width
    val height = bufferedImage.height
    val channel = bufferedImage.colorModel.numComponents
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val flippedY = height - 1 - y
        for (x in 0 until width) {
            pixels[y * width + x] = bufferedImage.getRGB(x, flippedY)
        }
    }
    return DefaultBitmap(width, height, channel, pixels)
}

/** The CPU decode: [createBitmap], with no platform image to copy. */
actual suspend fun decodeImage(bytes: ByteArray): DecodedImage = createBitmap(bytes).asDecodedImage()

/** Subsampled Desktop JVM decode using `ImageReadParam.setSourceSubsampling`. */
actual suspend fun decodeImage(bytes: ByteArray, mipSkip: Int): DecodedImage =
    decodeBitmapSubsampled(bytes, mipSkip).asDecodedImage()

/** This platform decodes an image on the calling thread. */
actual val canDecodeImageBlocking: Boolean = true

/** [decodeBitmap], with no platform image to copy. */
actual fun decodeImageBlocking(bytes: ByteArray): DecodedImage = decodeBitmap(bytes).asDecodedImage()

/** Subsampled blocking Desktop JVM decode. */
actual fun decodeImageBlocking(bytes: ByteArray, mipSkip: Int): DecodedImage =
    decodeBitmapSubsampled(bytes, mipSkip).asDecodedImage()
