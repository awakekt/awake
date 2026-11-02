/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class CursorImageTest {
    private fun pixels(w: Int, h: Int) = ByteArray(w * h * CursorImage.PIXEL_STRIDE)

    @Test
    fun keepsSizeAndHotspot() {
        val image = CursorImage(pixels(4, 6), width = 4, height = 6, hotspotX = 1, hotspotY = 2)
        assertEquals(4, image.width)
        assertEquals(6, image.height)
        assertEquals(1, image.hotspotX)
        assertEquals(2, image.hotspotY)
    }

    @Test
    fun hotspotDefaultsToTopLeft() {
        val image = CursorImage(pixels(2, 2), width = 2, height = 2)
        assertEquals(0, image.hotspotX)
        assertEquals(0, image.hotspotY)
    }

    @Test
    fun rejectsAPixelBufferThatIsNotWidthTimesHeightTimesFour() {
        assertFailsWith<IllegalArgumentException> { CursorImage(pixels(2, 2), width = 4, height = 4) }
    }

    @Test
    fun rejectsANonPositiveSize() {
        assertFailsWith<IllegalArgumentException> { CursorImage(ByteArray(0), width = 0, height = 0) }
    }

    @Test
    fun rejectsAHotspotOutsideTheImage() {
        assertFailsWith<IllegalArgumentException> { CursorImage(pixels(2, 2), width = 2, height = 2, hotspotX = 2, hotspotY = 0) }
    }

    @Test
    fun copiesThePixelsSoALaterChangeCannotAlterTheCursor() {
        val source = pixels(1, 1).also { it[0] = 1 }
        val image = CursorImage(source, width = 1, height = 1)
        source[0] = 99
        assertEquals(1, image.rgba[0], "the cursor keeps its own copy of the pixels")
    }

    @Test
    fun equalsByIdentitySoAHostCanCacheOneNativeCursorPerInstance() {
        val a = CursorImage(pixels(2, 2), width = 2, height = 2)
        val b = CursorImage(pixels(2, 2), width = 2, height = 2)
        assertNotEquals(a, b, "two images, even with equal pixels, are distinct cache keys")
        assertTrue(a == a)
    }
}
