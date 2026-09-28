/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.image.DefaultBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CubemapStripTest {

    @Test
    fun facesComeOffTheStripInOrderWithTheirTopRowFirst() {
        // Face f's image row y (0 = top) is red f * 16 + y. createBitmap stores the bottom row first.
        val pixels = IntArray(STRIP_WIDTH * SIZE) { index ->
            val face = (index % STRIP_WIDTH) / SIZE
            val imageRow = SIZE - 1 - index / STRIP_WIDTH
            OPAQUE or ((face * 16 + imageRow) shl RED_SHIFT)
        }

        val cubemap = DefaultBitmap(STRIP_WIDTH, SIZE, 4, pixels).cubemapFromStrip()

        assertTrue(cubemap.isCubemap)
        assertEquals(6, cubemap.layerCount)
        assertEquals(SIZE, cubemap.width)
        repeat(6) { face ->
            repeat(SIZE) { row ->
                val red = cubemap.data[(face * SIZE * SIZE + row * SIZE) * 4].toInt() and 0xFF
                assertEquals(face * 16 + row, red, "face $face row $row")
            }
        }
    }

    @Test
    fun aStripThatIsNotSixSquaresIsRejected() {
        assertFailsWith<IllegalArgumentException> { DefaultBitmap(10, 2, 4, IntArray(20)).cubemapFromStrip() }
    }

    private companion object {
        const val SIZE = 2
        const val STRIP_WIDTH = SIZE * 6
        const val OPAQUE = -0x1000000
        const val RED_SHIFT = 16
    }
}
