/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.testing

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Writes a diagnostic PNG. Alpha is intentionally dropped so cleared frames appear black. */
fun PixelMap.writePng(file: File): String {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val sample = sample(x, y)
            image.setRGB(x, y, (sample.r shl RED_SHIFT) or (sample.g shl GREEN_SHIFT) or sample.b)
        }
    }
    file.parentFile?.mkdirs()
    ImageIO.write(image, "png", file)
    return file.absolutePath
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
