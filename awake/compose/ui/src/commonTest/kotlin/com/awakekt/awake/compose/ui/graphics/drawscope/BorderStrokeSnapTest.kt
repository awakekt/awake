/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A border band of 1 device pixel or more snaps to whole pixels, half up, so a 1 dp border under a
 * fractional canvas scale stays one solid pixel; a hairline under 1 pixel keeps its width.
 */
class BorderStrokeSnapTest {
    @Test
    fun aBandOfAPixelOrMoreRoundsToWholePixels() {
        assertEquals(1f, snapBorderStroke(1f))
        assertEquals(1f, snapBorderStroke(1.1566582f))
        assertEquals(2f, snapBorderStroke(1.5f))
        assertEquals(2f, snapBorderStroke(2.4f))
        assertEquals(3f, snapBorderStroke(2.5f))
    }

    @Test
    fun aHairlineKeepsItsWidthAndNeverVanishes() {
        assertEquals(0.5f, snapBorderStroke(0.5f))
        assertEquals(0.999f, snapBorderStroke(0.999f))
        assertEquals(0.1f, snapBorderStroke(0.1f))
    }
}
