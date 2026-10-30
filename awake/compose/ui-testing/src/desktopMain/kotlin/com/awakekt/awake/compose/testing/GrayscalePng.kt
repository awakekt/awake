/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.render.capture.PixelMap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// The 8-bit grey PNGs `assertMatchesGrayscaleBaseline` records and reads back.

/**
 * A grey baseline as the opaque RGBA it stands for. Read from the raster's samples: `getRGB` on a
 * `TYPE_BYTE_GRAY` image converts through a linear grey colour space and returns 188 for a stored 128.
 */
internal fun expandGray(image: BufferedImage): BufferedImage {
    if (image.type != BufferedImage.TYPE_BYTE_GRAY) return image
    val rgba = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val v = image.raster.getSample(x, y, 0)
            rgba.setRGB(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
        }
    }
    return rgba
}

/**
 * [image] as a PNG at the writer's strongest compression. `ImageIO.write`'s default level leaves a large
 * text sheet about 40% bigger (124 KB against 88 KB for the 1.5x glyph box sheet); pixels are the same.
 */
internal fun writeSmallestPng(image: BufferedImage, file: File) {
    val writer = ImageIO.getImageWritersByFormatName("png").next()
    val params = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = 0f
    }
    // An image output stream over an existing file does not truncate it, so a shorter PNG would keep the old tail.
    file.delete()
    try {
        requireNotNull(ImageIO.createImageOutputStream(file)) { "Cannot write ${file.path}" }.use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), params)
        }
    } finally {
        writer.dispose()
    }
}

/** This frame as an 8-bit grey image, written to the raster directly; fails on a coloured or translucent pixel. */
internal fun PixelMap.toGrayImage(name: String): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val offset = (y * width + x) * 4
            val r = pixels[offset].toInt() and 0xFF
            val g = pixels[offset + 1].toInt() and 0xFF
            val b = pixels[offset + 2].toInt() and 0xFF
            val a = pixels[offset + 3].toInt() and 0xFF
            if (r != g || g != b || a != 0xFF) {
                throw AssertionError("\"$name\" cannot be a grey baseline: the pixel at $x, $y is rgba($r, $g, $b, $a).")
            }
            image.raster.setSample(x, y, 0, r)
        }
    }
    return image
}
