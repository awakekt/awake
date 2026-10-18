/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.color

import kotlin.test.Test
import kotlin.test.assertEquals

/** A colour writes as the hex a canvas element saves, and reads back to the same text. */
class ColorHexTest {
    @Test
    fun anOpaqueColourDropsItsAlpha() {
        assertEquals("#FFFFFF", Color.White.toHex(), "as a canvas element's default colour is written")
        assertEquals("#FFFFFF", Color.fromHex("#FFFFFFFF").toHex())
        assertEquals("#3366CC", Color(0.2f, 0.4f, 0.8f).toHex())
    }

    @Test
    fun aColourWithAlphaKeepsIt() {
        assertEquals("#00000000", Color.Transparent.toHex(), "as a canvas element's default background is written")
        assertEquals("#3366CC80", Color(0.2f, 0.4f, 0.8f, 0.5f).toHex(), "a half rounds up to 0x80")
        assertEquals("#000000FE", Color(0f, 0f, 0f, 254f / 255f).toHex(), "nearly opaque is not opaque")
    }

    @Test
    fun channelsAreClampedAndRounded() {
        assertEquals("#FF0000", Color(3f, -1f, 0f, 2f).toHex())
        assertEquals("#000000", Color(Float.NaN, 0f, 0f).toHex(), "NaN writes as 0 rather than failing")
        assertEquals("#808080", Color(0.5f, 0.5f, 0.5f).toHex())
    }

    @Test
    fun hexReadsBackToTheSameText() {
        listOf("#000000", "#7F7F7F", "#A0B1C2", "#11223380", "#A0B1C2D3", "#00000000").forEach { hex ->
            assertEquals(hex, Color.fromHex(hex).toHex())
        }
        assertEquals("#A0B1C2D3", Color.fromHex("#a0b1c2d3").toHex(), "either case reads; it writes upper case")
    }

    @Test
    fun theAlphaCanBeForcedOnOrOff() {
        assertEquals("#FFFFFFFF", Color.White.toHex(includeAlpha = true))
        assertEquals("#3366CC", Color(0.2f, 0.4f, 0.8f, 0.5f).toHex(includeAlpha = false))
    }
}
