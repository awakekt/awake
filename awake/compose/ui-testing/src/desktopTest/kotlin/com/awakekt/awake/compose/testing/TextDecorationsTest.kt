/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextShadow
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A style's outline and shadow (#630), drawn with each glyph in one draw from the font's effect field (#1061). */
class TextDecorationsTest {
    private fun glyphs(style: TextStyle): List<UiDrawPrimitive.Glyph> =
        composeFrame(200, 60) { Text("Hi", style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    @Test
    fun aShadowIsDrawnWithEachGlyphMovedByItsOffset() {
        val plain = glyphs(TextStyle(color = Color.White))
        val shadowed = glyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 2f, 3f, blur = 1.5f)))

        assertEquals(plain.size, shadowed.size, "one draw a glyph, its shadow included")
        assertTrue(shadowed.all { it.color == Color.White })
        for (glyph in shadowed) {
            val decoration = assertNotNull(glyph.decoration)
            assertEquals(Color.Black, decoration.shadowColor)
            assertEquals(2f to 3f, decoration.shadowOffsetX to decoration.shadowOffsetY)
            assertEquals(1.5f, decoration.shadowBlur)
            assertTrue(decoration.outlineColor.isTransparent(), "no outline asked for")
        }
    }

    @Test
    fun aTextFieldDrawsItsOutlineAndShadowToo() {
        val state = TextFieldState("Hi")
        fun fieldGlyphs(style: TextStyle) =
            composeFrame(200, 60) { BasicTextField(state, style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

        val plain = fieldGlyphs(TextStyle(color = Color.White))
        val decorated = fieldGlyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black), outline = TextOutline(Color.Black)))

        assertEquals(plain.size, decorated.size, "one draw a glyph, outline and shadow included")
        assertTrue(decorated.all { it.decoration != null })
    }

    /** The glyph's effect tile reaches past its quad on every side, by more than the outline, so the ring fits. */
    @Test
    fun anOutlineSurroundsEveryGlyphInItsOwnDraw() {
        val outlined = glyphs(TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f)))
        assertEquals(2, outlined.size)
        for (glyph in outlined) {
            val decoration = assertNotNull(glyph.decoration)
            assertEquals(Color.Black, decoration.outlineColor)
            assertEquals(2f, decoration.outlineWidth)
            assertTrue(decoration.fieldX < glyph.x - 2f && decoration.fieldY < glyph.y - 2f, "$decoration around $glyph")
            assertTrue(decoration.fieldX + decoration.fieldW > glyph.x + glyph.w + 2f, "$decoration around $glyph")
            assertTrue(decoration.fieldY + decoration.fieldH > glyph.y + glyph.h + 2f, "$decoration around $glyph")
        }
    }

    /** Below the pixel-glyph limit too: decorated text draws from the distance field, so its weight never jumps there. */
    @Test
    fun smallDecoratedTextDrawsFromTheDistanceFieldOnBothSidesOfThePixelGlyphLimit() {
        for (size in listOf(12f, 24f, 25f, 48f)) {
            val outlined = glyphs(TextStyle(color = Color.White, size = size.sp, outline = TextOutline(Color.Black, 1f)))
            assertTrue(outlined.isNotEmpty() && outlined.all { it.decoration != null }, "at $size px: $outlined")
        }
    }

    /** A font with no effect field, such as the bitmap font, stamps its decorated text as before: ten draws a glyph. */
    @Test
    fun aFontWithoutAnEffectFieldStampsTheDecoration() {
        val font = UiFonts.bitmap()
        val plain = glyphs(TextStyle(color = Color.White, font = font))
        val decorated = glyphs(TextStyle(color = Color.White, font = font, shadow = TextShadow(Color.Black), outline = TextOutline(Color.Black)))

        assertEquals(plain.size * 10, decorated.size, "a shadow, eight outline copies, then the text")
        assertEquals(plain, decorated.takeLast(plain.size))
        assertTrue(decorated.all { it.decoration == null })
        assertNull(font.effectGlyphFor('H'.code))
    }
}
