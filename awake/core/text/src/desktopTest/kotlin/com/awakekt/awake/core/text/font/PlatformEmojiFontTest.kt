/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformEmojiFontTest {

    @Test
    fun platformEmojiFontLoadsAndBakesColorRgbaAtlas() {
        val emojiFont = UiFonts.platformEmoji(cellSize = 16, emojis = "😀👍❤️")
        if (emojiFont == null) {
            println("Platform emoji font not available on this host environment; skipping host test")
            return
        }

        assertEquals(UiFontSamplingMode.ColorRgba, emojiFont.samplingMode)
        assertTrue(emojiFont.atlasWidth > 0)
        assertTrue(emojiFont.atlasHeight > 0)

        val grinning = 0x1F600 // 😀
        assertTrue(emojiFont.hasGlyph(grinning), "Should have glyph for grinning face")

        val rect = emojiFont.glyphFor(grinning, FontWeight.Normal)
        assertNotNull(rect, "Glyph rect for grinning face should not be null")
        assertTrue(rect.u1 > rect.u0)
        assertTrue(rect.v1 > rect.v0)

        val pixels = emojiFont.atlasPixelsRgba
        assertEquals(emojiFont.atlasWidth * emojiFont.atlasHeight * 4, pixels.size)

        // Verify that the atlas contains non-transparent coloured pixels
        var hasOpaqueColor = false
        for (i in 0 until pixels.size step 4) {
            val a = pixels[i + 3].toInt() and 0xFF
            val r = pixels[i].toInt() and 0xFF
            val g = pixels[i + 1].toInt() and 0xFF
            val b = pixels[i + 2].toInt() and 0xFF
            val hasColor = maxOf(r, g, b) > 50
            if (a > 100 && hasColor) {
                hasOpaqueColor = true
                break
            }
        }
        assertTrue(hasOpaqueColor, "Atlas should contain coloured emoji pixels")
    }

    @Test
    fun fallbackWithEmojiResolvesCorrectFontFaces() {
        val emojiFont = UiFonts.platformEmoji(cellSize = 16, emojis = "😀")
        if (emojiFont == null) return

        val primary = UiFonts.trueSans(16)
        val chained = UiFonts.fallback(primary, listOf(emojiFont))

        val grinning = 0x1F600
        assertTrue(chained.hasGlyph('A'.code))
        assertTrue(chained.hasGlyph(grinning))

        // Standard text glyph should resolve to the stacked fallback font
        val textFace = chained.fontFor('A'.code)
        assertEquals(chained, textFace)
        assertEquals(UiFontSamplingMode.DistanceField, textFace.samplingMode)

        // Emoji glyph should resolve to the ColorRgba emoji font
        val emojiFace = chained.fontFor(grinning)
        assertEquals(emojiFont, emojiFace)
        assertEquals(UiFontSamplingMode.ColorRgba, emojiFace.samplingMode)

        val width = chained.measureTextWidth("Hi 😀!", 16f)
        assertTrue(width > 20f, "Mixed string should have positive measured width: $width")
    }
}
