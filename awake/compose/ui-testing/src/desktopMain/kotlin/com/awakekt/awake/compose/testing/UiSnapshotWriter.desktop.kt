/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.capture.PixelMap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Rasterizes this [ComposeComponentFrame] to an image and writes it to [file].
 *
 * Uses Awake's software rasterizer to produce a pixel-accurate PNG snapshot on the JVM without
 * requiring GPU hardware, a window, or an emulator.
 */
fun ComposeComponentFrame.captureImage(
    file: File,
    width: Int = root.width.coerceAtLeast(1),
    height: Int = root.height.coerceAtLeast(1),
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): File {
    val image = primitives.rasterizeToPixelMap(width, height, background, font).toBufferedImage()
    file.parentFile?.mkdirs()
    ImageIO.write(image, "PNG", file)
    return file
}

fun PixelMap.toBufferedImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val sample = sample(x, y)
            image.setRGB(x, y, (sample.a shl 24) or (sample.r shl 16) or (sample.g shl 8) or sample.b)
        }
    }
    return image
}

/**
 * Rasterizes this [ComposeComponentFrame] to an image and writes it to [filePath].
 */
fun ComposeComponentFrame.captureImage(
    filePath: String,
    width: Int = root.width.coerceAtLeast(1),
    height: Int = root.height.coerceAtLeast(1),
    background: Color = Color(0.1f, 0.1f, 0.12f, 1f),
    font: UiFont? = null,
): File = captureImage(File(filePath), width, height, background, font)

fun ByteArray.toBufferedImage(width: Int, height: Int): BufferedImage = PixelMap(width, height, this).toBufferedImage()
