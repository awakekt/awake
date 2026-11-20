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

/**
 * A style's outline and shadow (#630), drawn with each glyph in one draw from the font's effect field (#1061). Under the
 * pixel-glyph limit the fill is plain text's own pixel glyphs, drawn over the decoration (#1808).
 */
class TextDecorationsTest {
    private fun glyphs(style: TextStyle): List<UiDrawPrimitive.Glyph> =
        composeFrame(200, 60) { Text("Hi", style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    @Test
    fun aShadowIsDrawnWithEachGlyphMovedByItsOffset() {
        val plain = glyphs(TextStyle(color = Color.White))
        val shadowed = glyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black, 2f, 3f, blur = 1.5f)))
        val decorations = shadowed.filter { it.decoration != null }

        assertEquals(plain.size, decorations.size, "one decoration a glyph")
        for (glyph in decorations) {
            val decoration = assertNotNull(glyph.decoration)
            assertEquals(Color.Black, decoration.shadowColor)
            assertEquals(2f to 3f, decoration.shadowOffsetX to decoration.shadowOffsetY)
            assertEquals(1.5f, decoration.shadowBlur)
            assertTrue(decoration.outlineColor.isTransparent(), "no outline asked for")
        }
    }

    /**
     * At or under the pixel-glyph limit a decorated run draws its decoration with no fill, every glyph of it first, then
     * exactly the glyphs a plain run of its size draws: two batches for the run, the plain one's fill to the pixel.
     */
    @Test
    fun smallDecoratedTextFillsFromThePlainRunsOwnPixelGlyphs() {
        for (size in listOf(11f, 12f, 14f, 20f, 24f)) {
            val plain = glyphs(TextStyle(color = Color.White, size = size.sp))
            val decorated = glyphs(TextStyle(color = Color.White, size = size.sp, outline = TextOutline(Color.Black), shadow = TextShadow(Color.Black)))

            assertEquals(plain.size * 2, decorated.size, "at $size px: one decoration and one fill a glyph")
            val (under, over) = decorated.take(plain.size) to decorated.drop(plain.size)
            assertTrue(under.all { it.decoration != null && it.color.isTransparent() }, "at $size px the decoration comes first, unfilled: $under")
            assertEquals(plain, over, "at $size px the fill is the plain run's")
        }
    }

    @Test
    fun aTextFieldDrawsItsOutlineAndShadowToo() {
        val state = TextFieldState("Hi")
        fun fieldGlyphs(style: TextStyle) =
            composeFrame(200, 60) { BasicTextField(state, style = style) }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

        val plain = fieldGlyphs(TextStyle(color = Color.White))
        val decorated = fieldGlyphs(TextStyle(color = Color.White, shadow = TextShadow(Color.Black), outline = TextOutline(Color.Black)))

        assertEquals(plain.size, decorated.count { it.decoration != null }, "one decoration a glyph, outline and shadow included")
        assertEquals(plain, decorated.filter { it.decoration == null }, "the fill is the plain field's")
    }

    /** The glyph's effect tile reaches past its quad on every side, by more than the outline, so the ring fits. */
    @Test
    fun anOutlineSurroundsEveryGlyphInItsOwnDraw() {
        val outlined = glyphs(TextStyle(color = Color.White, outline = TextOutline(Color.Black, 2f))).filter { it.decoration != null }
        assertEquals(2, outlined.size)
        for (glyph in outlined) {
            val decoration = assertNotNull(glyph.decoration)
            assertEquals(Color.Black, decoration.outlineColor)
            assertEquals(2f, decoration.outlineWidth)
            assertTrue(decoration.fieldOffsetX < -2f && decoration.fieldOffsetY < -2f, "$decoration around $glyph")
            assertTrue(decoration.fieldOffsetX + decoration.fieldW > glyph.w + 2f, "$decoration around $glyph")
            assertTrue(decoration.fieldOffsetY + decoration.fieldH > glyph.h + 2f, "$decoration around $glyph")
        }
    }

    /** The decoration draws from the effect field on both sides of the pixel-glyph limit, so its weight never jumps there. */
    @Test
    fun theDecorationDrawsFromTheEffectFieldOnBothSidesOfThePixelGlyphLimit() {
        for (size in listOf(12f, 24f, 25f, 48f)) {
            val outlined = glyphs(TextStyle(color = Color.White, size = size.sp, outline = TextOutline(Color.Black, 1f)))
            val decorations = outlined.filter { it.decoration != null }
            assertEquals(2, decorations.size, "at $size px: $outlined")
            // Above the limit the field draw fills too, as plain text there draws from the field.
            if (size > UiFonts.default().pixelGlyphMaxSize) assertEquals(outlined, decorations, "at $size px")
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
