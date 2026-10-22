/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmojiTextRasterTest {

    @Test
    fun mixedTextAndEmojiSplitsFontFacesAndRastersUntintedColor() {
        val emojiFont = UiFonts.platformEmoji(cellSize = 16, emojis = "😀")
        if (emojiFont == null) {
            println("No platform emoji font available on this host environment; skipping test")
            return
        }

        val fallback = UiFonts.fallback(UiFonts.default(), listOf(emojiFont))
        val frame = composeFrame(200, 40) {
            CompositionLocalProvider(LocalFont, fallback) {
                Text(
                    text = "Hi 😀!",
                    style = TextStyle(size = 16.sp, color = Color.White),
                )
            }
        }

        val glyphs = frame.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()
        assertTrue(glyphs.isNotEmpty(), "Should emit glyph primitives")

        // Emoji glyph should carry the emojiFont face with ColorRgba sampling mode
        val emojiGlyph = glyphs.find { it.font?.samplingMode == UiFontSamplingMode.ColorRgba }
        assertNotNull(emojiGlyph, "Expected an emoji glyph sampled with ColorRgba")
        assertEquals(emojiFont, emojiGlyph.font)

        // Normal text glyphs should retain the primary font
        val textGlyphs = glyphs.filter { it.font?.samplingMode != UiFontSamplingMode.ColorRgba }
        assertTrue(textGlyphs.isNotEmpty(), "Expected text glyphs with standard font")

        // Rasterize to RGBA pixel map
        val pixels = frame.primitives.rasterize(200, 40, background = Color.Black, font = fallback)
        assertEquals(200 * 40 * 4, pixels.size)

        // Check for colored pixels around the emoji glyph position
        val ex = emojiGlyph.x.toInt().coerceIn(0, 199)
        val ey = emojiGlyph.y.toInt().coerceIn(0, 39)
        val ew = emojiGlyph.w.toInt().coerceAtLeast(1)
        val eh = emojiGlyph.h.toInt().coerceAtLeast(1)

        var hasColoredPixel = false
        for (y in ey until (ey + eh).coerceAtMost(40)) {
            for (x in ex until (ex + ew).coerceAtMost(200)) {
                val idx = (y * 200 + x) * 4
                val r = pixels[idx].toInt() and 0xFF
                val g = pixels[idx + 1].toInt() and 0xFF
                val b = pixels[idx + 2].toInt() and 0xFF
                val a = pixels[idx + 3].toInt() and 0xFF
                if (a > 50 && (r != g || g != b)) {
                    hasColoredPixel = true
                    break
                }
            }
            if (hasColoredPixel) break
        }
        assertTrue(hasColoredPixel, "Rendered emoji glyph should produce colored (non-greyscale) pixels")
    }
}
