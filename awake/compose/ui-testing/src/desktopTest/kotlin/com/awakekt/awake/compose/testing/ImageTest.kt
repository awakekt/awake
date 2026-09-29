/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.ContentScale
import com.awakekt.awake.compose.foundation.Image
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageTest {

    /** 2x2, top row red then green, bottom row blue then white. */
    private val quadrants = ImageBitmap(
        2,
        2,
        byteArrayOf(
            -1, 0, 0, -1, 0, -1, 0, -1,
            0, 0, -1, -1, -1, -1, -1, -1,
        ),
    )

    private fun ByteArray.rgbAt(width: Int, x: Int, y: Int): Triple<Int, Int, Int> {
        val offset = (y * width + x) * 4
        return Triple(this[offset].toInt() and 0xFF, this[offset + 1].toInt() and 0xFF, this[offset + 2].toInt() and 0xFF)
    }

    @Test
    fun drawsTheImageTheRightWayUp() {
        val frame = composeFrame(40, 40) {
            Image(quadrants, contentDescription = null, modifier = Modifier.size(40.dp), contentScale = ContentScale.FillBounds)
        }
        val pixels = frame.primitives.rasterize(40, 40)
        assertEquals(Triple(255, 0, 0), pixels.rgbAt(40, 10, 10), "top left is the image's top-left pixel")
        assertEquals(Triple(0, 255, 0), pixels.rgbAt(40, 30, 10), "top right")
        assertEquals(Triple(0, 0, 255), pixels.rgbAt(40, 10, 30), "bottom left")
        assertEquals(Triple(255, 255, 255), pixels.rgbAt(40, 30, 30), "bottom right")
    }

    @Test
    fun cropFillsTheNodeAndFitLeavesBands() {
        val wide = ImageBitmap(4, 2, ByteArray(4 * 2 * 4) { if (it % 4 == 3) -1 else 0 })
        val background = Color(1f, 1f, 1f, 1f)
        fun darkRows(scale: ContentScale): Int {
            val frame = composeFrame(20, 20) {
                Image(wide, contentDescription = null, modifier = Modifier.size(20.dp), contentScale = scale)
            }
            val pixels = frame.primitives.rasterize(20, 20, background)
            return (0 until 20).count { y -> pixels.rgbAt(20, 10, y).first < 128 }
        }
        assertEquals(20, darkRows(ContentScale.Crop), "Crop covers the square node")
        assertEquals(10, darkRows(ContentScale.Fit), "Fit keeps the 2:1 image at half the height")
    }

    @Test
    fun describedImageIsAnImageWithALabel() {
        val frame = composeFrame(40, 40) {
            Image(quadrants, contentDescription = "Harbor Town preview", modifier = Modifier.size(40.dp))
        }
        val node = frame.flatSemantics().single { SemanticsProperties.Label in it.config }
        assertEquals("Harbor Town preview", node.config[SemanticsProperties.Label])
    }
}
