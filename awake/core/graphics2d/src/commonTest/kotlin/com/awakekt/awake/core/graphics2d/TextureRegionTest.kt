/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextureRegionTest {

    @Test
    fun texelsBecomeFractionsOfTheTexture() {
        // The second 32x32 icon of a 64x64 sheet's top row.
        assertEquals(TextureRegion(0.5f, 0f, 1f, 0.5f), TextureRegion.ofTexels(Rectangle(32f, 0f, 32f, 32f), 64, 64))
    }

    @Test
    fun flippingSwapsTheEdgesOfOneAxis() {
        val icon = TextureRegion(0f, 0f, 0.5f, 0.5f)

        assertEquals(TextureRegion(0.5f, 0f, 0f, 0.5f), icon.flippedHorizontally())
        assertEquals(TextureRegion(0f, 0.5f, 0.5f, 0f), icon.flippedVertically())
        assertEquals(icon, icon.flippedHorizontally().flippedHorizontally())
    }

    @Test
    fun anEmptyTextureHasNoTexels() {
        assertFailsWith<IllegalArgumentException> { TextureRegion.ofTexels(Rectangle(0f, 0f, 1f, 1f), 0, 1) }
    }
}
