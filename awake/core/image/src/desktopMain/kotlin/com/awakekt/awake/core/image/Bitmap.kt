/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

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
fun decodeBitmap(bytes: ByteArray): Bitmap {
    val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
    val width = bufferedImage.width
    val height = bufferedImage.height
    val channel = bufferedImage.colorModel.numComponents
    val flipY = true
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val flippedY = if (flipY) height - 1 - y else y
            val color = bufferedImage.getRGB(x, flippedY)
            pixels[y * width + x] = color
        }
    }

    return DefaultBitmap(width, height, channel, pixels)
}
