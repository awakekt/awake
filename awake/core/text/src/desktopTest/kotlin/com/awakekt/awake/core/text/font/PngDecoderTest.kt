/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class PngDecoderTest {

    @Test
    fun decodesStandardRgbaPng() {
        val width = 4
        val height = 4
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Red, Green, Blue, Semi-transparent Yellow
        img.setRGB(0, 0, 0xFFFF0000.toInt())
        img.setRGB(1, 0, 0xFF00FF00.toInt())
        img.setRGB(2, 0, 0xFF0000FF.toInt())
        img.setRGB(3, 0, 0x80FFFF00.toInt())

        val stream = ByteArrayOutputStream()
        ImageIO.write(img, "png", stream)
        val bytes = stream.toByteArray()

        val decoded = PngDecoder.decode(bytes)
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        assertEquals(width * height * 4, decoded.rgba.size)

        // Pixel (0, 0): Red (255, 0, 0, 255)
        assertEquals(255, decoded.rgba[0].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[1].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[2].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[3].toInt() and 0xFF)

        // Pixel (1, 0): Green (0, 255, 0, 255)
        assertEquals(0, decoded.rgba[4].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[5].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[6].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[7].toInt() and 0xFF)

        // Pixel (2, 0): Blue (0, 0, 255, 255)
        assertEquals(0, decoded.rgba[8].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[9].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[10].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[11].toInt() and 0xFF)

        // Pixel (3, 0): Semi-transparent Yellow (255, 255, 0, 128)
        assertEquals(255, decoded.rgba[12].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[13].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[14].toInt() and 0xFF)
        assertEquals(128, decoded.rgba[15].toInt() and 0xFF)
    }
}
